package com.badminton.booking.repository;

import com.badminton.booking.entity.CourtType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourtTypeRepository extends JpaRepository<CourtType, Long> {
}