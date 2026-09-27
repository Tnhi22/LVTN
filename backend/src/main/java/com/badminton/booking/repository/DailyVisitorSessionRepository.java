package com.badminton.booking.repository;

import com.badminton.booking.entity.DailyVisitorSession;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select s from DailyVisitorSession s where s.id = :id")
        Optional<DailyVisitorSession> findByIdForPayment(
                @Param("id") Long id
        );
}