package com.badminton.booking.controller;

import com.badminton.booking.dto.CourtMaintenanceRequest;
import com.badminton.booking.entity.CourtMaintenance;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.service.CourtMaintenanceService;
import com.badminton.booking.service.MaintenanceEditService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/court-maintenances")
public class CourtMaintenanceController {

    private final CourtMaintenanceService
            maintenanceService;

    public CourtMaintenanceController(
            CourtMaintenanceService maintenanceService, MaintenanceEditService editService) {

        this.maintenanceService =
                maintenanceService;
        this.editService = editService;
    }

    private final MaintenanceEditService editService;

    // STAFF hoặc ADMIN tạo lịch bảo trì/báo sự cố
    @PostMapping
    public CourtMaintenance createMaintenance(
            @RequestBody CourtMaintenanceRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập"
            );
        }

        Long creatorId =
                Long.valueOf(jwt.getSubject());

        return maintenanceService
                .createMaintenance(
                        request,
                        creatorId
                );
    }

    // Xem lịch sử bảo trì/sự cố của một sân
    @GetMapping("/court/{courtId}")
    public List<CourtMaintenance>
    getCourtMaintenanceHistory(
            @PathVariable Long courtId,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập"
            );
        }

        return maintenanceService
                .getCourtMaintenanceHistory(
                        courtId
                );
    }



    // STAFF hoặc ADMIN xác nhận đã hoàn thành bảo trì/sự cố
@PatchMapping("/{maintenanceId}/complete")
public CourtMaintenance completeMaintenance(
        @PathVariable Long maintenanceId,
        @AuthenticationPrincipal Jwt jwt) {

    if (jwt == null) {
        throw new BusinessException(
                HttpStatus.UNAUTHORIZED,
                "Vui lòng đăng nhập"
        );
    }

    Long staffId = Long.valueOf(jwt.getSubject());

    return maintenanceService.completeMaintenance(
            maintenanceId,
            staffId
    );
}

    @PutMapping("/{maintenanceId}")
    public CourtMaintenance updateMaintenance(@PathVariable Long maintenanceId,
            @RequestBody CourtMaintenanceRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        return editService.update(maintenanceId, Long.valueOf(jwt.getSubject()), request);
    }

    @PatchMapping("/{maintenanceId}/cancel")
    public CourtMaintenance cancelMaintenance(@PathVariable Long maintenanceId,
            @RequestBody java.util.Map<String, String> request, @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        return editService.cancel(maintenanceId, Long.valueOf(jwt.getSubject()), request.get("reason"));
    }
}
