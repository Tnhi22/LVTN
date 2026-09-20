package com.badminton.booking.repository;

import com.badminton.booking.entity.CourtPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourtPriceRepository
        extends JpaRepository<CourtPrice, Long> {

    Optional<CourtPrice> findByCourtTypeIdAndActiveTrue(
            Long courtTypeId
    );

    boolean existsByCourtTypeId(Long courtTypeId);
}
