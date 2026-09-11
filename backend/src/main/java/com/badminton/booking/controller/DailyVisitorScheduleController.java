package com.badminton.booking.controller;

import com.badminton.booking.entity.DailyVisitorSchedule;
import com.badminton.booking.repository.DailyVisitorScheduleRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/daily-visitor-schedules")
public class DailyVisitorScheduleController {

    private final DailyVisitorScheduleRepository scheduleRepository;

    public DailyVisitorScheduleController(
            DailyVisitorScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    @GetMapping
    public List<DailyVisitorSchedule> getAllSchedules() {
        return scheduleRepository.findByActiveTrue();
    }

    @GetMapping("/court/{courtId}")
    public List<DailyVisitorSchedule> getSchedulesByCourt(
            @PathVariable Long courtId) {

        return scheduleRepository.findByCourtIdAndActiveTrue(courtId);
    }
}