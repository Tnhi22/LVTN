package com.badminton.booking.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BookingReceipt(
        Long bookingId,
        String bookingStatus,
        Long courtAmount,
        Long shuttlecockAmount,
        Long totalAmount,
        String paymentMethod,
        Long amountReceived,
        Long changeAmount,
        LocalDateTime completedAt,
        Long completedBy,
        List<BookingBillItem> items
) {}