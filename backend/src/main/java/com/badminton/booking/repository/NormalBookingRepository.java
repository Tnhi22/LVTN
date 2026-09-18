package com.badminton.booking.repository;

import com.badminton.booking.entity.NormalBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;

public interface NormalBookingRepository extends JpaRepository<NormalBooking, Long> {

    List<NormalBooking> findByCourtIdAndBookingDateAndStatusNot(
            Long courtId,
            LocalDate bookingDate,
            String status
    );
    long countByUserIdAndBookingDateBetweenAndStatusNot(
        Long userId,
        LocalDate startDate,
        LocalDate endDate,
        String status
    );
    List<NormalBooking>
    findByUser_IdOrderByBookingDateDescStartTimeDesc(Long userId);
}