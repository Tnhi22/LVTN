package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorSchedule;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.repository.DailyVisitorScheduleRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DailyVisitorSessionService {

    private final DailyVisitorScheduleRepository scheduleRepository;
    private final DailyVisitorSessionRepository sessionRepository;

    public DailyVisitorSessionService(
            DailyVisitorScheduleRepository scheduleRepository,
            DailyVisitorSessionRepository sessionRepository) {

        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
    }

    // =========================================================
    // 1. Generate session cho một ngày cụ thể
    // =========================================================
    public List<DailyVisitorSession> generateSessions(LocalDate date) {

        List<DailyVisitorSchedule> schedules =
                scheduleRepository.findByActiveTrue();

        List<DailyVisitorSession> sessions = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();

        for (DailyVisitorSchedule schedule : schedules) {

            // Không tạo trùng cùng schedule + ngày
            boolean exists =
                    sessionRepository.existsByScheduleIdAndSessionDate(
                            schedule.getId(),
                            date
                    );

            if (exists) {
                continue;
            }

            DailyVisitorSession session =
                    new DailyVisitorSession();

            session.setSchedule(schedule);
            session.setSessionDate(date);
            session.setStartTime(schedule.getStartTime());
            session.setEndTime(schedule.getEndTime());
            session.setMinParticipants(
                    schedule.getMinParticipants()
            );
            session.setMaxParticipants(
                    schedule.getMaxParticipants()
            );

            LocalDateTime sessionEnd =
                    LocalDateTime.of(
                            date,
                            schedule.getEndTime()
                    );

            // Nếu session đã kết thúc thì CLOSED
            if (!sessionEnd.isAfter(now)) {
                session.setStatus("CLOSED");
            } else {
                session.setStatus("OPEN");
            }

            sessions.add(session);
        }

        return sessionRepository.saveAll(sessions);
    }


    // =========================================================
    // 2. Khi backend vừa khởi động
    // Luôn đảm bảo có session:
    // hôm nay + ngày mai + ngày kia
    // =========================================================
    @PostConstruct
    public void initRollingSessions() {

        LocalDate today = LocalDate.now();

        generateSessions(today);
        generateSessions(today.plusDays(1));
        generateSessions(today.plusDays(2));
    }


    // =========================================================
    // 3. Tự động chạy mỗi ngày lúc 00:05
    // Đảm bảo luôn có rolling 3 ngày
    // =========================================================
    @Scheduled(cron = "0 5 0 * * *")
    public void generateRollingSessions() {

        LocalDate today = LocalDate.now();

        generateSessions(today);
        generateSessions(today.plusDays(1));
        generateSessions(today.plusDays(2));
    }
}