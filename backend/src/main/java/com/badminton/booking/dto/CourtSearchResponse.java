package com.badminton.booking.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record CourtSearchResponse(
        Long courtId,
        String courtName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        long durationMinutes,
        long totalPrice
) {
}