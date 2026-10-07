package com.badminton.booking.dto;

import jakarta.validation.constraints.*;

public record AdminUserUpdateRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone) {}
