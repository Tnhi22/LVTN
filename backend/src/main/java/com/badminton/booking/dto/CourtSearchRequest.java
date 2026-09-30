package com.badminton.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.time.LocalTime;

public record CourtSearchRequest(
        @NotNull(message = "Vui lòng chọn ngày chơi")
        LocalDate date,

        @NotNull(message = "Vui lòng chọn giờ bắt đầu")
        LocalTime startTime,

        @NotNull(message = "Vui lòng chọn giờ kết thúc")
        LocalTime endTime,

        @Positive(message = "ID sân phải lớn hơn 0")
        Long courtId,

        @PositiveOrZero(message = "Ngân sách không được âm")
        Long maxTotalPrice
) {
}