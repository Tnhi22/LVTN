package com.badminton.booking.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long id,
        String fullName,
        String email,
        String phone,
        String role
) {
}