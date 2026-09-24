package com.badminton.booking.repository;

import com.badminton.booking.entity.DailyVisitorParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;

public interface DailyVisitorParticipantRepository
        extends JpaRepository<DailyVisitorParticipant, Long> {

    List<DailyVisitorParticipant> findBySessionId(Long sessionId);

    long countBySessionIdAndStatus(
            Long sessionId,
            String status
    );

    boolean existsBySessionIdAndUserId(
            Long sessionId,
            Long userId
    );

        @Query("""
        SELECT COALESCE(SUM(p.slotCount), 0)
        FROM DailyVisitorParticipant p
        WHERE p.session.id = :sessionId
        AND p.status <> 'CANCELLED'
        """)
        Long getUsedSlots(@Param("sessionId") Long sessionId);

        @Query("""
        SELECT COALESCE(SUM(p.checkedInSlots), 0)
        FROM DailyVisitorParticipant p
        WHERE p.session.id = :sessionId
        AND p.status <> 'CANCELLED'
        """)
        Long getCheckedInSlots(@Param("sessionId") Long sessionId);

}