package com.badminton.booking.controller;

import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.service.DailyVisitorParticipantService;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/session/{sessionId}")
    public List<DailyVisitorParticipant> getParticipantsBySession(
            @PathVariable Long sessionId) {

        return participantRepository.findBySessionId(sessionId);
    }

    @PostMapping("/register")
    public DailyVisitorParticipant register(
            @RequestParam Long sessionId,
            @RequestParam Long userId) {

        return participantService.register(
                sessionId,
                userId
        );
    }
    @GetMapping("/test")
    public String test() {
        return "Daily Visitor Participant Controller is working!";
    }
}