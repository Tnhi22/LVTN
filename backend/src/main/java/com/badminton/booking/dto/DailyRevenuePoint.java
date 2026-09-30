package com.badminton.booking.dto;

import java.time.LocalDate;

public record DailyRevenuePoint(
        LocalDate date,
        int paidNormalBookings,
        long courtRevenue,
        long shuttlecockRevenue,
        long totalRevenue
) {}