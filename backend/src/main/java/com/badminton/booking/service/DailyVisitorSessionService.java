package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorSchedule;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.repository.DailyVisitorScheduleRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    public List<DailyVisitorSession> generateSessions(LocalDate date) {

        List<DailyVisitorSchedule> schedules =
                scheduleRepository.findByActiveTrue();

        List<DailyVisitorSession> sessions = new ArrayList<>();

        for (DailyVisitorSchedule schedule : schedules) {

            boolean exists =
                    sessionRepository.existsByScheduleIdAndSessionDate(
                            schedule.getId(),
                            date
                    );

            if (exists) {
                continue;
            }

            DailyVisitorSession session = new DailyVisitorSession();

            session.setSchedule(schedule);
            session.setSessionDate(date);
            session.setStartTime(schedule.getStartTime());
            session.setEndTime(schedule.getEndTime());
            session.setMinParticipants(schedule.getMinParticipants());
            session.setMaxParticipants(schedule.getMaxParticipants());
            session.setStatus("OPEN");

            sessions.add(session);
        }

        return sessionRepository.saveAll(sessions);
    }
}