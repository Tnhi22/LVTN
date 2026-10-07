package com.badminton.booking.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
public record MaintenanceCompleteRequest(
        @NotNull(message = "Nhập chi phí thực tế")
        @PositiveOrZero(message = "Chi phí không được âm") Long maintenanceCost,
        @NotBlank(message = "Nhập nội dung hư hỏng và công việc đã sửa")
        @Size(max = 5000, message = "Nội dung tối đa 5000 ký tự") String repairDetail
) {}
