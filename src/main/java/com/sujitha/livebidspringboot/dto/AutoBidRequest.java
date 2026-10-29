package com.sujitha.livebidspringboot.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class AutoBidRequest {

    @NotNull(message = "Maximum auto-bid amount is required.")
    @DecimalMin(
            value = "0.01",
            message = "Maximum auto-bid amount must be greater than zero."
    )
    private BigDecimal maxAmount;

    public AutoBidRequest() {
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }
}