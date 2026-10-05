package com.badminton.booking.controller;

import com.badminton.booking.dto.DailyVisitorWaitlistResponse;
import com.badminton.booking.entity.DailyVisitorWaitlist;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.service.DailyVisitorWaitlistService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/daily-visitor-waitlists")
public class DailyVisitorWaitlistController {

    private final DailyVisitorWaitlistService waitlistService;

    public DailyVisitorWaitlistController(
            DailyVisitorWaitlistService waitlistService) {

        this.waitlistService = waitlistService;
    }

    // Tham gia danh sách chờ.
    @Transactional
    @PostMapping("/session/{sessionId}/join")
    public DailyVisitorWaitlistResponse join(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = getUserId(jwt);

        DailyVisitorWaitlist entry =
                waitlistService.joinWaitlist(sessionId, userId);

        return waitlistService.toResponse(entry);
    }

    // Xem trạng thái và vị trí của chính mình.
    @GetMapping("/session/{sessionId}/my")
    public DailyVisitorWaitlistResponse getMyStatus(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        return waitlistService.getMyStatus(
                sessionId,
                getUserId(jwt)
        );
    }

    // Xác nhận lời mời nhận slot.
    @Transactional
    @PostMapping("/session/{sessionId}/confirm")
    public DailyVisitorWaitlistResponse confirm(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = getUserId(jwt);

        DailyVisitorWaitlist entry =
                waitlistService.confirmOffer(sessionId, userId);

        return waitlistService.toResponse(entry);
    }

    // Rời hàng chờ hoặc từ chối lời mời.
    @Transactional
    @DeleteMapping("/session/{sessionId}/leave")
    public DailyVisitorWaitlistResponse leave(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = getUserId(jwt);

        DailyVisitorWaitlist entry =
                waitlistService.leaveWaitlist(sessionId, userId);

        return waitlistService.toResponse(entry);
    }

    private Long getUserId(Jwt jwt) {

        if (jwt == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập"
            );
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Thông tin đăng nhập không hợp lệ"
            );
        }
    }
}