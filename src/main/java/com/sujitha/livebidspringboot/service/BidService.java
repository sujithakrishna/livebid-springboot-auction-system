
package com.sujitha.livebidspringboot.service;

import com.sujitha.livebidspringboot.dao.AuctionDAO;
import com.sujitha.livebidspringboot.dao.BidDAO;
import com.sujitha.livebidspringboot.dao.NotificationDAO;
import com.sujitha.livebidspringboot.dto.BidUpdateMessage;
import com.sujitha.livebidspringboot.dto.PlaceBidRequest;
import com.sujitha.livebidspringboot.exception.BusinessValidationException;
import com.sujitha.livebidspringboot.exception.OptimisticLockException;
import com.sujitha.livebidspringboot.model.Auction;
import com.sujitha.livebidspringboot.model.Bid;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BidService {

    private static final int MAX_RETRIES = 3;

    private final BidDAO bidDAO;
    private final AuctionDAO auctionDAO;
    private final NotificationDAO notificationDAO;
    private final SimpMessagingTemplate messagingTemplate;
    private final AutoBidService autoBidService;

    public BidService(
            BidDAO bidDAO,
            AuctionDAO auctionDAO,
            NotificationDAO notificationDAO,
            SimpMessagingTemplate messagingTemplate,
            AutoBidService autoBidService) {

        this.bidDAO = bidDAO;
        this.auctionDAO = auctionDAO;
        this.notificationDAO = notificationDAO;
        this.messagingTemplate = messagingTemplate;
        this.autoBidService = autoBidService;
    }

    // ---------------------------------------------------------
    // PLACE BID
    // ---------------------------------------------------------

    public Bid placeBid(
            long auctionId,
            long bidderId,
            PlaceBidRequest request) {

        if (auctionId <= 0) {
            throw new BusinessValidationException(
                    "Auction ID must be greater than zero."
            );
        }

        if (bidderId <= 0) {
            throw new BusinessValidationException(
                    "Bidder ID must be greater than zero."
            );
        }

        if (!bidDAO.userExists(bidderId)) {
            throw new IllegalArgumentException(
                    "Bidder not found with ID: " + bidderId
            );
        }

        if (request == null) {
            throw new BusinessValidationException(
                    "Bid request is required."
            );
        }

        if (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessValidationException(
                    "Bid amount must be greater than zero."
            );
        }

        Auction auction = auctionDAO.findById(auctionId);

        if (auction == null) {
            throw new IllegalArgumentException(
                    "Auction not found with ID: " + auctionId
            );
        }

        validateAuction(auction);

        if (request.getAmount()
                .compareTo(auction.getCurrentHighestBid()) <= 0) {
            throw new BusinessValidationException(
                    "Your bid must be greater than the current highest bid of "
                            + auction.getCurrentHighestBid() + "."
            );
        }

        LocalDateTime operationStartedAt =
                LocalDateTime.now().withNano(0);

        Bid bid = new Bid(
                auctionId,
                bidderId,
                request.getAmount(),
                request.isAutoBid()
        );

        long expectedVersion = auction.getVersion();

        Long previousHighestBidderId =
                auction.getCurrentHighestBidderId();

        // -----------------------------------------------------
        // OPTIMISTIC LOCK RETRY
        // -----------------------------------------------------

        for (int attempt = 1;
             attempt <= MAX_RETRIES;
             attempt++) {

            try {
                Bid savedBid = bidDAO.placeBid(
                        bid,
                        expectedVersion,
                        previousHighestBidderId
                );

                Auction latestAuction = auctionDAO.findById(auctionId);

                if (latestAuction == null) {
                    throw new IllegalArgumentException(
                            "Auction not found with ID: " + auctionId
                    );
                }

                BidUpdateMessage updateMessage = new BidUpdateMessage(
                        savedBid.getAuctionId(),
                        savedBid.getId(),
                        savedBid.getBidderId(),
                        savedBid.getAmount(),
                        savedBid.getPlacedAt(),
                        savedBid.isAutoBid(),
                        latestAuction.getCurrentHighestBid(),
                        latestAuction.getCurrentHighestBidderId(),
                        latestAuction.getEndTime()
                );

                // Broadcast the successful bid to subscribers.
                messagingTemplate.convertAndSend(
                        "/topic/auction/" + auctionId,
                        updateMessage
                );

                // Allow active auto-bids to respond to the new bid.
                autoBidService.processAutoBids(auctionId);

                return savedBid;

            } catch (OptimisticLockException exception) {

                if (attempt == MAX_RETRIES) {
                    break;
                }

                Auction latestAuction = auctionDAO.findById(auctionId);

                if (latestAuction == null) {
                    throw new IllegalArgumentException(
                            "Auction not found with ID: " + auctionId
                    );
                }

                validateAuction(latestAuction);

                int comparison = request.getAmount().compareTo(
                        latestAuction.getCurrentHighestBid()
                );

                Long latestHighestBidderId =
                        latestAuction.getCurrentHighestBidderId();

                // Another bidder has beaten or matched this bid.
                if (comparison <= 0) {

                    if (latestHighestBidderId != null
                            && latestHighestBidderId.longValue() != bidderId) {

                        notifyConcurrentLoserIfNeeded(
                                bidderId,
                                auctionId,
                                operationStartedAt
                        );
                    }

                    throw new BusinessValidationException(
                            "Your bid of " + request.getAmount()
                                    + " was outbid. Current highest bid is "
                                    + latestAuction.getCurrentHighestBid() + "."
                    );
                }

                // Our bid remains valid. Retry against the latest version.
                expectedVersion = latestAuction.getVersion();
                previousHighestBidderId =
                        latestAuction.getCurrentHighestBidderId();
            }
        }

        // All retries failed. Notify only if another user now holds
        // an equal or higher bid; a generic conflict alone is not an OUTBID.
        Auction latestAuction = auctionDAO.findById(auctionId);

        if (latestAuction == null) {
            throw new IllegalArgumentException(
                    "Auction not found with ID: " + auctionId
            );
        }

        Long latestHighestBidderId =
                latestAuction.getCurrentHighestBidderId();

        boolean genuinelyOutbid =
                request.getAmount().compareTo(
                        latestAuction.getCurrentHighestBid()
                ) <= 0
                        && latestHighestBidderId != null
                        && latestHighestBidderId.longValue() != bidderId;

        if (genuinelyOutbid) {
            notifyConcurrentLoserIfNeeded(
                    bidderId,
                    auctionId,
                    operationStartedAt
            );

            throw new BusinessValidationException(
                    "Your bid was outbid. Current highest bid is "
                            + latestAuction.getCurrentHighestBid() + "."
            );
        }

        throw new BusinessValidationException(
                "Bid could not be completed because the auction changed "
                        + "concurrently. Please retry your bid."
        );
    }

    // ---------------------------------------------------------
    // GET BID HISTORY
    // ---------------------------------------------------------

    public List<Bid> getBidHistory(long auctionId) {

        if (auctionId <= 0) {
            throw new BusinessValidationException(
                    "Auction ID must be greater than zero."
            );
        }

        if (auctionDAO.findById(auctionId) == null) {
            throw new IllegalArgumentException(
                    "Auction not found with ID: " + auctionId
            );
        }

        return bidDAO.findByAuctionId(auctionId);
    }

    // ---------------------------------------------------------
    // VALIDATE AUCTION
    // ---------------------------------------------------------

    private void validateAuction(Auction auction) {

        if (!"LIVE".equals(auction.getStatus())) {
            throw new BusinessValidationException(
                    "Auction is not LIVE."
            );
        }

        if (auction.getEndTime() == null
                || !auction.getEndTime().isAfter(LocalDateTime.now())) {
            throw new BusinessValidationException(
                    "Auction has ended."
            );
        }
    }

    // ---------------------------------------------------------
    // CONCURRENT LOSER NOTIFICATION
    // ---------------------------------------------------------

    private void notifyConcurrentLoserIfNeeded(
            long bidderId,
            long auctionId,
            LocalDateTime operationStartedAt) {

        boolean alreadyExists = notificationDAO.existsSince(
                bidderId,
                auctionId,
                "OUTBID",
                operationStartedAt
        );

        if (!alreadyExists) {
            notificationDAO.createNotification(
                    bidderId,
                    auctionId,
                    "OUTBID"
            );
        }
    }
}
