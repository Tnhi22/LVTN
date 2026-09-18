package com.badminton.booking.dto;

public record PhoneRegisterResponse(
        Long id,
        String fullName,
        String phone,
        String role,
        String status,
        Boolean phoneVerified,
        String message
) {
}