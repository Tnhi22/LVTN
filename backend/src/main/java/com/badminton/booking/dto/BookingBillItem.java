package com.badminton.booking.dto;

import java.time.LocalDateTime;

public record BookingBillItem(
        Long issueId, Long productId, String productName,
        Integer quantityTubes, Integer quantityPieces,
        Long unitPrice, Long totalAmount, LocalDateTime issuedAt,
        boolean cancelled, LocalDateTime cancelledAt, Long cancelledBy,
        String cancelReason
) {}
