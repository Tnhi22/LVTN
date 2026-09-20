package com.badminton.booking.repository;

import com.badminton.booking.entity.NormalBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface NormalBookingRepository
        extends JpaRepository<NormalBooking, Long> {

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
    findByUser_IdOrderByBookingDateDescStartTimeDesc(
            Long userId
    );

    // Lấy booking của user theo trạng thái
    // Dùng để tự hủy các booking PENDING khi user bị WARNING
    List<NormalBooking> findByUser_IdAndStatus(
            Long userId,
            String status
    );
}