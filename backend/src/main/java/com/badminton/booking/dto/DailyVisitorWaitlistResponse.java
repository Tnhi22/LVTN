package com.badminton.booking.dto;

import com.badminton.booking.entity.WaitlistStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record DailyVisitorWaitlistResponse(
        Long id,
        Long sessionId,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        WaitlistStatus status,
        Integer position,
        LocalDateTime createdAt,
        LocalDateTime offeredAt,
        LocalDateTime offerExpiresAt,
        Long participantId,
        String message
) {
}