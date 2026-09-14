package com.badminton.booking.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class BookingRequest {

    private Long userId;

    // Giữ lại để tương thích booking 1 sân cũ
    private Long courtId;

    // Booking nhiều sân
    private List<Long> courtIds;

    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;

    private String walkInName;
    private String walkInPhone;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCourtId() {
        return courtId;
    }

    public void setCourtId(Long courtId) {
        this.courtId = courtId;
    }

    public List<Long> getCourtIds() {
        return courtIds;
    }

    public void setCourtIds(List<Long> courtIds) {
        this.courtIds = courtIds;
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

    public String getWalkInName() {
        return walkInName;
    }

    public void setWalkInName(String walkInName) {
        this.walkInName = walkInName;
    }

    public String getWalkInPhone() {
        return walkInPhone;
    }

    public void setWalkInPhone(String walkInPhone) {
        this.walkInPhone = walkInPhone;
    }
}