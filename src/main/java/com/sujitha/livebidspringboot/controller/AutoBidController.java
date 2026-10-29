package com.sujitha.livebidspringboot.controller;

import com.sujitha.livebidspringboot.dto.AutoBidRequest;
import com.sujitha.livebidspringboot.model.AutoBid;
import com.sujitha.livebidspringboot.service.AutoBidService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auctions")
public class AutoBidController {

    private final AutoBidService autoBidService;

    public AutoBidController(
            AutoBidService autoBidService) {

        this.autoBidService = autoBidService;
    }

    // ---------------------------------------------------------
    // SET / UPDATE AUTO-BID
    // ---------------------------------------------------------

    @PostMapping("/{auctionId}/auto-bid")
    public AutoBid setAutoBid(
            @PathVariable long auctionId,
            @Valid @RequestBody AutoBidRequest request,
            Authentication authentication) {

        long userId =
                (Long) authentication.getPrincipal();

        return autoBidService.setAutoBid(
                auctionId,
                userId,
                request
        );
    }

    // ---------------------------------------------------------
    // DEACTIVATE AUTO-BID
    // ---------------------------------------------------------

    @DeleteMapping("/{auctionId}/auto-bid")
    public String deactivateAutoBid(
            @PathVariable long auctionId,
            Authentication authentication) {

        long userId =
                (Long) authentication.getPrincipal();

        autoBidService.deactivateAutoBid(
                auctionId,
                userId
        );

        return "Auto-bid deactivated successfully.";
    }
}