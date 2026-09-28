package com.badminton.booking.dto;

public record BookingAddItemRequest(
        Long productId,
        Integer quantityTubes,
        Integer quantityPieces
) {}
