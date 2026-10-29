package com.sujitha.livebidspringboot.service;

import com.sujitha.livebidspringboot.dao.AuctionDAO;
import com.sujitha.livebidspringboot.dto.CreateAuctionRequest;
import com.sujitha.livebidspringboot.model.Auction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class AuctionService {

    private static final Set<String> VALID_STATUSES =
            Set.of(
                    "SCHEDULED",
                    "LIVE",
                    "CLOSED"
            );

    private final AuctionDAO auctionDAO;

    public AuctionService(AuctionDAO auctionDAO) {
        this.auctionDAO = auctionDAO;
    }

    // ---------------------------------------------------------
    // GET / SEARCH / FILTER AUCTIONS
    // ---------------------------------------------------------

    public List<Auction> getAllAuctions(
            String status,
            String search,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean endingSoon) {

        // -----------------------------------------------------
        // VALIDATE STATUS
        // -----------------------------------------------------

        if (status != null && !status.isBlank()) {

            status = status.trim().toUpperCase();

            if (!VALID_STATUSES.contains(status)) {

                throw new IllegalArgumentException(
                        "Invalid auction status. "
                                + "Allowed values: SCHEDULED, LIVE, CLOSED."
                );
            }
        }

        // -----------------------------------------------------
        // VALIDATE SEARCH
        // -----------------------------------------------------

        if (search != null) {

            search = search.trim();

            if (search.isEmpty()) {
                search = null;
            }
        }

        // -----------------------------------------------------
        // VALIDATE MINIMUM PRICE
        // -----------------------------------------------------

        if (minPrice != null &&
                minPrice.compareTo(BigDecimal.ZERO) < 0) {

            throw new IllegalArgumentException(
                    "Minimum price cannot be negative."
            );
        }

        // -----------------------------------------------------
        // VALIDATE MAXIMUM PRICE
        // -----------------------------------------------------

        if (maxPrice != null &&
                maxPrice.compareTo(BigDecimal.ZERO) < 0) {

            throw new IllegalArgumentException(
                    "Maximum price cannot be negative."
            );
        }

        // -----------------------------------------------------
        // VALIDATE PRICE RANGE
        // -----------------------------------------------------

        if (minPrice != null &&
                maxPrice != null &&
                minPrice.compareTo(maxPrice) > 0) {

            throw new IllegalArgumentException(
                    "Minimum price cannot be greater than maximum price."
            );
        }

        // -----------------------------------------------------
        // ENDING SOON
        // -----------------------------------------------------

        if (endingSoon) {

            /*
             * Ending-soon auctions must be LIVE.
             * The DAO also enforces this condition.
             */
            status = "LIVE";
        }

        return auctionDAO.findAll(
                status,
                search,
                minPrice,
                maxPrice,
                endingSoon
        );
    }

    // ---------------------------------------------------------
    // GET AUCTION BY ID
    // ---------------------------------------------------------

    public Auction getAuctionById(
            long auctionId) {

        if (auctionId <= 0) {

            throw new IllegalArgumentException(
                    "Auction ID must be greater than zero."
            );
        }

        Auction auction =
                auctionDAO.findById(auctionId);

        if (auction == null) {

            throw new IllegalArgumentException(
                    "Auction not found with ID: "
                            + auctionId
            );
        }

        return auction;
    }

    // ---------------------------------------------------------
    // CREATE AUCTION
    // ---------------------------------------------------------

    public Auction createAuction(
            long sellerId,
            CreateAuctionRequest request) {

        if (sellerId <= 0) {

            throw new IllegalArgumentException(
                    "Seller ID must be greater than zero."
            );
        }

        if (request.getStartTime()
                .isBefore(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "Auction start time cannot be in the past."
            );
        }

        if (!request.getEndTime()
                .isAfter(request.getStartTime())) {

            throw new IllegalArgumentException(
                    "Auction end time must be after start time."
            );
        }

        Auction auction = new Auction();

        /*
         * Seller ID comes from the authenticated JWT.
         * It does NOT come from the request body.
         */
        auction.setSellerId(sellerId);

        auction.setTitle(
                request.getTitle().trim()
        );

        auction.setDescription(
                request.getDescription().trim()
        );

        auction.setImageUrl(
                request.getImageUrl()
        );

        auction.setStartingPrice(
                request.getStartingPrice()
        );

        // Initially, highest bid equals starting price.
        auction.setCurrentHighestBid(
                request.getStartingPrice()
        );

        // No bidder has bid yet.
        auction.setCurrentHighestBidderId(
                null
        );

        auction.setStartTime(
                request.getStartTime()
        );

        auction.setEndTime(
                request.getEndTime()
        );

        // Newly created auctions are scheduled.
        auction.setStatus(
                "SCHEDULED"
        );

        // Initial optimistic-lock version.
        auction.setVersion(0);

        return auctionDAO.save(auction);
    }
}