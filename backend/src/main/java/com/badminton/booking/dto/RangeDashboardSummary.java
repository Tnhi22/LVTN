package com.badminton.booking.dto;

import java.time.LocalDate;

public record RangeDashboardSummary(
        LocalDate from,
        LocalDate to,
        int paidNormalBookings,
        long courtRevenue,
        long shuttlecockRevenue,
        long normalBookingRevenue,
        long soldTubes,
        long soldPieces
) {}