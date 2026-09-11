package com.badminton.booking.repository;

import com.badminton.booking.entity.DailyVisitorSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyVisitorSessionRepository
        extends JpaRepository<DailyVisitorSession, Long> {

    List<DailyVisitorSession> findBySessionDate(LocalDate sessionDate);

    List<DailyVisitorSession> findBySessionDateAndStatus(
            LocalDate sessionDate,
            String status
    );

    List<DailyVisitorSession> findByScheduleIdAndSessionDate(
            Long scheduleId,
            LocalDate sessionDate
    );
       boolean existsByScheduleIdAndSessionDate(
            Long scheduleId,
            LocalDate sessionDate
    );
}