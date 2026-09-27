package com.badminton.booking.dto;

public record SettlementRequest(
        String paymentMethod,
        Long amountReceived
) {}