package com.badminton.booking.dashboard;

import com.badminton.booking.dto.InventoryDashboardItem;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

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

    // STAFF vÃ  ADMIN xem tá»•ng quan kho hÃ ng
    @GetMapping("/inventory")
    public List<InventoryDashboardItem>
    getInventoryDashboard(
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lÃ²ng Ä‘Äƒng nháº­p"
            );
        }

        Long staffId =
                Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "KhÃ´ng tÃ¬m tháº¥y tÃ i khoáº£n"
                        )
                );

        String role = staff.getRole();

        if (!"STAFF".equals(role)
                && !"ADMIN".equals(role)) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chá»‰ STAFF hoáº·c ADMIN Ä‘Æ°á»£c xem kho"
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
                    "Vui lÃ²ng Ä‘Äƒng nháº­p"
            );
        }

        Long staffId =
                Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "KhÃ´ng tÃ¬m tháº¥y tÃ i khoáº£n"
                        )
                );

        String role = staff.getRole();

        if (!"STAFF".equals(role)
                && !"ADMIN".equals(role)) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chá»‰ STAFF hoáº·c ADMIN Ä‘Æ°á»£c xem kho"
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
                    "Vui lÃ²ng Ä‘Äƒng nháº­p"
            );
        }

        Long staffId =
                Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "KhÃ´ng tÃ¬m tháº¥y tÃ i khoáº£n"
                        )
                );

        String role = staff.getRole();

        if (!"STAFF".equals(role)
                && !"ADMIN".equals(role)) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chá»‰ STAFF hoáº·c ADMIN Ä‘Æ°á»£c xem dashboard"
            );
        }

        return dashboardService
                .getTodayActivitySummary();
    }



        @GetMapping("/date/{date}")
        public TodayActivitySummary getSummaryByDate(
                @PathVariable
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
                throw new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "Vui lòng đăng nhập"
                );
        }

        Long staffId = Long.valueOf(jwt.getSubject());
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy tài khoản"
                ));

        if (!"STAFF".equals(staff.getRole())
                && !"ADMIN".equals(staff.getRole())) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Chỉ STAFF hoặc ADMIN được xem dashboard"
                );
        }

        return dashboardService.getActivitySummaryByDate(date);
        }



        // STAFF vÃ  ADMIN xem dashboard báº£o trÃ¬ sÃ¢n
        @GetMapping("/maintenance")
        public MaintenanceDashboardSummary getMaintenanceDashboard(
                @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
                throw new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "Vui lÃ²ng Ä‘Äƒng nháº­p"
                );
        }

        Long staffId = Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "KhÃ´ng tÃ¬m tháº¥y tÃ i khoáº£n"
                        )
                );

        String role = staff.getRole();

        if (!"STAFF".equals(role)
                && !"ADMIN".equals(role)) {

                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Chá»‰ STAFF hoáº·c ADMIN Ä‘Æ°á»£c xem dashboard báº£o trÃ¬"
                );
        }

        return dashboardService.getMaintenanceDashboard();
        }
}
