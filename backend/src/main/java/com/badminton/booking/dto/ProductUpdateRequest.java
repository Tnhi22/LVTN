package com.badminton.booking.dto;

import jakarta.validation.constraints.*;

public record ProductUpdateRequest(
        @NotBlank String name,
        @NotBlank String brand,
        @NotNull @Positive Integer piecesPerTube,
        @NotNull @Positive Long tubePrice,
        @Positive Long piecePrice,
        @NotBlank String imageUrl,
        @NotNull @PositiveOrZero Integer minimumStockTubes,
        @NotNull @Positive Integer targetStockTubes
) {
}