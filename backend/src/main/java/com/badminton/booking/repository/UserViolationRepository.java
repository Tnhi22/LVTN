package com.badminton.booking.repository;

import com.badminton.booking.entity.UserViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserViolationRepository
        extends JpaRepository<UserViolation, Long> {

    List<UserViolation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
        SELECT v
        FROM UserViolation v
        WHERE v.user.id = :userId
          AND v.userStatusAfter = :status
        ORDER BY v.createdAt DESC
    """)
    List<UserViolation> findWarningHistory(
            @Param("userId") Long userId,
            @Param("status") String status
    );
}