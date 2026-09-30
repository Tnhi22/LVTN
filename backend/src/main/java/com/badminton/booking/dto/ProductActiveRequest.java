package com.badminton.booking.dto;

import jakarta.validation.constraints.NotNull;

public record ProductActiveRequest(
        @NotNull Boolean active
) {
}