package com.badminton.booking.dto;

import java.time.LocalDate;

public class TodayActivitySummary {

    private LocalDate date;

    private Integer totalNormalBookings;
    private Integer pendingNormalBookings;
    private Integer checkedInNormalBookings;
    private Integer cancelledNormalBookings;
    private Integer noShowNormalBookings;

    private Long normalBookingRevenue;

    private Integer totalDailyVisitorSessions;
    private Integer openDailyVisitorSessions;
    private Integer cancelledDailyVisitorSessions;
    private Long dailyVisitorCheckedInSlots;

    public TodayActivitySummary() {
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getTotalNormalBookings() {
        return totalNormalBookings;
    }

    public void setTotalNormalBookings(
            Integer totalNormalBookings) {
        this.totalNormalBookings =
                totalNormalBookings;
    }

    public Integer getPendingNormalBookings() {
        return pendingNormalBookings;
    }

    public void setPendingNormalBookings(
            Integer pendingNormalBookings) {
        this.pendingNormalBookings =
                pendingNormalBookings;
    }

    public Integer getCheckedInNormalBookings() {
        return checkedInNormalBookings;
    }

    public void setCheckedInNormalBookings(
            Integer checkedInNormalBookings) {
        this.checkedInNormalBookings =
                checkedInNormalBookings;
    }

    public Integer getCancelledNormalBookings() {
        return cancelledNormalBookings;
    }

    public void setCancelledNormalBookings(
            Integer cancelledNormalBookings) {
        this.cancelledNormalBookings =
                cancelledNormalBookings;
    }

    public Integer getNoShowNormalBookings() {
        return noShowNormalBookings;
    }

    public void setNoShowNormalBookings(
            Integer noShowNormalBookings) {
        this.noShowNormalBookings =
                noShowNormalBookings;
    }

    public Long getNormalBookingRevenue() {
        return normalBookingRevenue;
    }

    public void setNormalBookingRevenue(
            Long normalBookingRevenue) {
        this.normalBookingRevenue =
                normalBookingRevenue;
    }

    public Integer getTotalDailyVisitorSessions() {
        return totalDailyVisitorSessions;
    }

    public void setTotalDailyVisitorSessions(
            Integer totalDailyVisitorSessions) {
        this.totalDailyVisitorSessions =
                totalDailyVisitorSessions;
    }

    public Integer getOpenDailyVisitorSessions() {
        return openDailyVisitorSessions;
    }

    public void setOpenDailyVisitorSessions(
            Integer openDailyVisitorSessions) {
        this.openDailyVisitorSessions =
                openDailyVisitorSessions;
    }

    public Integer getCancelledDailyVisitorSessions() {
        return cancelledDailyVisitorSessions;
    }

    public void setCancelledDailyVisitorSessions(
            Integer cancelledDailyVisitorSessions) {
        this.cancelledDailyVisitorSessions =
                cancelledDailyVisitorSessions;
    }

    public Long getDailyVisitorCheckedInSlots() {
        return dailyVisitorCheckedInSlots;
    }

    public void setDailyVisitorCheckedInSlots(
            Long dailyVisitorCheckedInSlots) {
        this.dailyVisitorCheckedInSlots =
                dailyVisitorCheckedInSlots;
    }
}