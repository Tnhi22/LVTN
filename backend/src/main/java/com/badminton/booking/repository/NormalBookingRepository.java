package com.badminton.booking.repository;

import com.badminton.booking.entity.NormalBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface NormalBookingRepository extends JpaRepository<NormalBooking, Long> {

    List<NormalBooking> findByCourtIdAndBookingDateAndStatusNot(
            Long courtId,
            LocalDate bookingDate,
            String status
    );
}