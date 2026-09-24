package com.badminton.booking.dto;

import java.time.LocalDateTime;

public class MaintenanceDashboardItem {

    private Long maintenanceId;
    private Long courtId;
    private String courtName;

    private String type;
    private String status;
    private String reason;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Integer maintenanceIntervalMonths;
    private LocalDateTime nextMaintenanceAt;

    /*
     * IN_PROGRESS
     * SCHEDULED
     * OVERDUE
     * DUE_SOON
     */
    private String alertStatus;

    public MaintenanceDashboardItem() {
    }

    public Long getMaintenanceId() {
        return maintenanceId;
    }

    public void setMaintenanceId(Long maintenanceId) {
        this.maintenanceId = maintenanceId;
    }

    public Long getCourtId() {
        return courtId;
    }

    public void setCourtId(Long courtId) {
        this.courtId = courtId;
    }

    public String getCourtName() {
        return courtName;
    }

    public void setCourtName(String courtName) {
        this.courtName = courtName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getMaintenanceIntervalMonths() {
        return maintenanceIntervalMonths;
    }

    public void setMaintenanceIntervalMonths(
            Integer maintenanceIntervalMonths) {
        this.maintenanceIntervalMonths =
                maintenanceIntervalMonths;
    }

    public LocalDateTime getNextMaintenanceAt() {
        return nextMaintenanceAt;
    }

    public void setNextMaintenanceAt(
            LocalDateTime nextMaintenanceAt) {
        this.nextMaintenanceAt = nextMaintenanceAt;
    }

    public String getAlertStatus() {
        return alertStatus;
    }

    public void setAlertStatus(String alertStatus) {
        this.alertStatus = alertStatus;
    }
}