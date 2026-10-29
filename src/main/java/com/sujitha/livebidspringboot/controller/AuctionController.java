package com.sujitha.livebidspringboot.controller;

import com.sujitha.livebidspringboot.dto.CreateAuctionRequest;
import com.sujitha.livebidspringboot.dto.PlaceBidRequest;
import com.sujitha.livebidspringboot.model.Auction;
import com.sujitha.livebidspringboot.model.Bid;
import com.sujitha.livebidspringboot.service.AuctionService;
import com.sujitha.livebidspringboot.service.BidService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionService auctionService;
    private final BidService bidService;

    public AuctionController(
            AuctionService auctionService,
            BidService bidService) {

        this.auctionService = auctionService;
        this.bidService = bidService;
    }

    // ---------------------------------------------------------
    // GET / SEARCH / FILTER AUCTIONS
    // ---------------------------------------------------------

    @GetMapping
    public List<Auction> getAllAuctions(

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            BigDecimal minPrice,

            @RequestParam(required = false)
            BigDecimal maxPrice,

            @RequestParam(defaultValue = "false")
            boolean endingSoon) {

        return auctionService.getAllAuctions(
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

    @GetMapping("/{id}")
    public Auction getAuctionById(
            @PathVariable long id) {

        return auctionService.getAuctionById(id);
    }

    // ---------------------------------------------------------
    // CREATE AUCTION
    // ---------------------------------------------------------

    @PostMapping
    public Auction createAuction(
            @Valid @RequestBody CreateAuctionRequest request,
            Authentication authentication) {

        long sellerId =
                (Long) authentication.getPrincipal();

        return auctionService.createAuction(
                sellerId,
                request
        );
    }

    // ---------------------------------------------------------
    // PLACE BID
    // ---------------------------------------------------------

    @PostMapping("/{auctionId}/bids")
    public Bid placeBid(
            @PathVariable long auctionId,
            @Valid @RequestBody PlaceBidRequest request,
            Authentication authentication) {

        long bidderId =
                (Long) authentication.getPrincipal();

        return bidService.placeBid(
                auctionId,
                bidderId,
                request
        );
    }

    // ---------------------------------------------------------
    // GET BID HISTORY
    // ---------------------------------------------------------

    @GetMapping("/{auctionId}/bids")
    public List<Bid> getBids(
            @PathVariable long auctionId) {

        return bidService.getBidHistory(
                auctionId
        );
    }
}