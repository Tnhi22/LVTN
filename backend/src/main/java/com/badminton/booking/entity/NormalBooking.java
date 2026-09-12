package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "normal_bookings")
public class NormalBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime checkedInAt;

    private Long checkedInBy;
    private String complaintStatus = "NONE";
    private String complaintReason;
    private LocalDateTime complainedAt;
    private LocalDateTime complaintResolvedAt;
    private Long complaintResolvedBy;

    public NormalBooking() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Court getCourt() {
        return court;
    }

    public void setCourt(Court court) {
        this.court = court;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public Long getCheckedInBy() {
        return checkedInBy;
    }

    public void setCheckedInBy(Long checkedInBy) {
        this.checkedInBy = checkedInBy;
    }

    public String getComplaintStatus() {
    return complaintStatus;
    }

    public void setComplaintStatus(String complaintStatus) {
        this.complaintStatus = complaintStatus;
    }

    public String getComplaintReason() {
        return complaintReason;
    }

    public void setComplaintReason(String complaintReason) {
        this.complaintReason = complaintReason;
    }

    public LocalDateTime getComplainedAt() {
        return complainedAt;
    }

    public void setComplainedAt(LocalDateTime complainedAt) {
        this.complainedAt = complainedAt;
    }

    public LocalDateTime getComplaintResolvedAt() {
        return complaintResolvedAt;
    }

    public void setComplaintResolvedAt(LocalDateTime complaintResolvedAt) {
        this.complaintResolvedAt = complaintResolvedAt;
    }

    public Long getComplaintResolvedBy() {
        return complaintResolvedBy;
    }

    public void setComplaintResolvedBy(Long complaintResolvedBy) {
        this.complaintResolvedBy = complaintResolvedBy;
    }
}