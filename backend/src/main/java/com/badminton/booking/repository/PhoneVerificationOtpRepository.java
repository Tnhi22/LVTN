package com.badminton.booking.repository;

import com.badminton.booking.entity.PhoneVerificationOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PhoneVerificationOtpRepository
        extends JpaRepository<PhoneVerificationOtp, Long> {

    Optional<PhoneVerificationOtp>
    findTopByUser_IdAndUsedFalseOrderByCreatedAtDesc(
            Long userId
    );
}