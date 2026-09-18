package com.badminton.booking.controller;

import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.service.DailyVisitorParticipantService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

@RestController
@RequestMapping("/api/daily-visitor-participants")
public class DailyVisitorParticipantController {

    private final DailyVisitorParticipantRepository participantRepository;
    private final DailyVisitorParticipantService participantService;

    public DailyVisitorParticipantController(
            DailyVisitorParticipantRepository participantRepository,
            DailyVisitorParticipantService participantService) {

        this.participantRepository = participantRepository;
        this.participantService = participantService;
    }

    // Xem danh sách đăng ký của một session
    @GetMapping("/session/{sessionId}")
    public List<DailyVisitorParticipant> getParticipantsBySession(
            @PathVariable Long sessionId) {

        return participantRepository.findBySessionId(sessionId);
    }

    // CUSTOMER tự đăng ký online
    // 1 tài khoản = 1 slot
    @PostMapping("/register")
    public DailyVisitorParticipant register(
            @RequestParam Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = Long.valueOf(jwt.getSubject());

        return participantService.register(
                sessionId,
                userId
        );
    }

    // STAFF đăng ký khách tại quầy
    // Chỉ cần SĐT người đại diện + số slot
    @PostMapping("/register-walk-in")
    public DailyVisitorParticipant registerWalkIn(
            @RequestParam Long sessionId,
            @RequestParam String fullName,
            @RequestParam String phone,
            @RequestParam Integer slotCount) {

        return participantService.registerWalkIn(
                sessionId,
                fullName,
                phone,
                slotCount
        );
    }

    @GetMapping("/test")
    public String test() {
        return "Daily Visitor Participant Controller is working!";
    }


    // STAFF / ADMIN check-in Daily Visitor
    @PostMapping("/{participantId}/check-in")
    public DailyVisitorParticipant checkIn(
            @PathVariable Long participantId,
            @AuthenticationPrincipal Jwt jwt) {

        Long staffId = Long.valueOf(jwt.getSubject());

        return participantService.checkIn(
                participantId,
                staffId
        );
    }

        // CUSTOMER tự hủy Daily Visitor trên web
    @DeleteMapping("/{participantId}/cancel")
    public DailyVisitorParticipant cancelByCustomer(
            @PathVariable Long participantId,
            @RequestParam Long userId) {

        return participantService.cancelByCustomer(
                participantId,
                userId
        );
    }

    // CUSTOMER hủy đăng ký online của chính mình


// STAFF / ADMIN hủy đăng ký khách tại quầy
@DeleteMapping("/{participantId}/staff-cancel")
public DailyVisitorParticipant cancelWalkInByStaff(
        @PathVariable Long participantId,
        @AuthenticationPrincipal Jwt jwt) {

    Long staffId = Long.valueOf(jwt.getSubject());

    return participantService.cancelWalkInByStaff(
            participantId,
            staffId
    );
}
}
