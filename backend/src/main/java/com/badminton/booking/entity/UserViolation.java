package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_violations")
public class UserViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private NormalBooking booking;

    @Column(name = "violation_type", nullable = false)
    private String violationType;

    @Column(name = "user_status_after", nullable = false)
    private String userStatusAfter;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public UserViolation() {
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public NormalBooking getBooking() {
        return booking;
    }

    public void setBooking(NormalBooking booking) {
        this.booking = booking;
    }

    public String getViolationType() {
        return violationType;
    }

    public void setViolationType(String violationType) {
        this.violationType = violationType;
    }

    public String getUserStatusAfter() {
        return userStatusAfter;
    }

    public void setUserStatusAfter(String userStatusAfter) {
        this.userStatusAfter = userStatusAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}