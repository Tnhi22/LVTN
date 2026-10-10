package com.badminton.booking.repository;

import com.badminton.booking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select u from User u where u.id = :id")
        Optional<User> findByIdForVerification(
                @Param("id") Long id
        );
        
}