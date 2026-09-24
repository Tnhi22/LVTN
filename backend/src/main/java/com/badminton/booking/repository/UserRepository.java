package com.badminton.booking.repository;

import com.badminton.booking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderId(
            String providerId
    );

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}