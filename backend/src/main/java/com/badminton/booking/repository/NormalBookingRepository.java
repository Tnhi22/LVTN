package com.badminton.booking.repository;

import com.badminton.booking.entity.NormalBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

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

    List<NormalBooking> findByBookingDate(
        LocalDate bookingDate
        );

                /*
        * Tìm booking chưa check-in bị ảnh hưởng bởi
        * một khoảng thời gian bảo trì hoặc sự cố.
        *
        * Điều kiện giao nhau:
        * bookingStart < maintenanceEnd
        * bookingEnd > maintenanceStart
        */
        @Query(
                value = """
                        SELECT b.*
                        FROM normal_bookings b
                        WHERE b.court_id = :courtId
                        AND b.status IN (
                        'PENDING',
                        'NO_SHOW_PENDING'
                        )
                        AND (
                        b.booking_date + b.start_time
                        ) < :endTime
                        AND (
                        b.booking_date + b.end_time
                        ) > :startTime
                        ORDER BY
                        b.booking_date ASC,
                        b.start_time ASC
                        """,
                nativeQuery = true
        )
        List<NormalBooking> findBookingsAffectedByMaintenance(
                @Param("courtId") Long courtId,
                @Param("startTime") LocalDateTime startTime,
                @Param("endTime") LocalDateTime endTime
        );
}