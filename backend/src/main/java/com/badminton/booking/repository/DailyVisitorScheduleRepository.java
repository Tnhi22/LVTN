package com.badminton.booking.repository;

import com.badminton.booking.entity.DailyVisitorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyVisitorScheduleRepository
        extends JpaRepository<DailyVisitorSchedule, Long> {

    List<DailyVisitorSchedule> findByActiveTrue();

    List<DailyVisitorSchedule> findByCourtIdAndActiveTrue(Long courtId);

}