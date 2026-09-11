package com.badminton.booking.controller;

import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.service.DailyVisitorSessionService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/daily-visitor-sessions")
public class DailyVisitorSessionController {

    private final DailyVisitorSessionRepository sessionRepository;
    private final DailyVisitorSessionService sessionService;

    public DailyVisitorSessionController(
            DailyVisitorSessionRepository sessionRepository,
            DailyVisitorSessionService sessionService) {

        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
    }

    @GetMapping
    public List<DailyVisitorSession> getAllSessions() {
        return sessionRepository.findAll();
    }

    @GetMapping("/date/{date}")
    public List<DailyVisitorSession> getSessionsByDate(
            @PathVariable LocalDate date) {

        return sessionRepository.findBySessionDate(date);
    }

    @GetMapping("/date/{date}/open")
    public List<DailyVisitorSession> getOpenSessionsByDate(
            @PathVariable LocalDate date) {

        return sessionRepository.findBySessionDateAndStatus(
                date,
                "OPEN"
        );
    }

    @PostMapping("/generate/{date}")
    public List<DailyVisitorSession> generateSessions(
            @PathVariable LocalDate date) {

        return sessionService.generateSessions(date);
    }
}