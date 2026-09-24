package com.badminton.booking.controller;

import com.badminton.booking.dto.InventoryDashboardItem;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.service.DashboardService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.badminton.booking.dto.InventoryDashboardSummary;
import com.badminton.booking.dto.TodayActivitySummary;
import com.badminton.booking.dto.MaintenanceDashboardSummary;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    public DashboardController(
            DashboardService dashboardService,
            UserRepository userRepository) {

        this.dashboardService = dashboardService;
        this.userRepository = userRepository;
    }

    // STAFF và ADMIN xem tổng quan kho hàng
    @GetMapping("/inventory")
    public List<InventoryDashboardItem>
    getInventoryDashboard(
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập"
            );
        }

        Long staffId =
                Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy tài khoản"
                        )
                );

        String role = staff.getRole();

        if (!"STAFF".equals(role)
                && !"ADMIN".equals(role)) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ STAFF hoặc ADMIN được xem kho"
            );
        }

        return dashboardService
                .getInventoryDashboard();
    }


    @GetMapping("/inventory/summary")
    public InventoryDashboardSummary
    getInventorySummary(
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập"
            );
        }

        Long staffId =
                Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy tài khoản"
                        )
                );

        String role = staff.getRole();

        if (!"STAFF".equals(role)
                && !"ADMIN".equals(role)) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ STAFF hoặc ADMIN được xem kho"
            );
        }

        return dashboardService.getInventorySummary();
    }


    @GetMapping("/today")
    public TodayActivitySummary getTodaySummary(
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập"
            );
        }

        Long staffId =
                Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy tài khoản"
                        )
                );

        String role = staff.getRole();

        if (!"STAFF".equals(role)
                && !"ADMIN".equals(role)) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ STAFF hoặc ADMIN được xem dashboard"
            );
        }

        return dashboardService
                .getTodayActivitySummary();
    }

        // STAFF và ADMIN xem dashboard bảo trì sân
        @GetMapping("/maintenance")
        public MaintenanceDashboardSummary getMaintenanceDashboard(
                @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
                throw new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "Vui lòng đăng nhập"
                );
        }

        Long staffId = Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy tài khoản"
                        )
                );

        String role = staff.getRole();

        if (!"STAFF".equals(role)
                && !"ADMIN".equals(role)) {

                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Chỉ STAFF hoặc ADMIN được xem dashboard bảo trì"
                );
        }

        return dashboardService.getMaintenanceDashboard();
        }
}