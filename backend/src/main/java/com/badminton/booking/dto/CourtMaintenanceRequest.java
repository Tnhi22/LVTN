package com.badminton.booking.dto;

import java.time.LocalDateTime;

public class CourtMaintenanceRequest {

    private Long courtId;
    private String type;
    private String reason;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long maintenanceCost;

    public CourtMaintenanceRequest() {
    }

    public Long getCourtId() {
        return courtId;
    }

    public void setCourtId(Long courtId) {
        this.courtId = courtId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public void setStartTime(
            LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(
            LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getMaintenanceCost() {
        return maintenanceCost;
    }

    public void setMaintenanceCost(
            Long maintenanceCost) {
        this.maintenanceCost = maintenanceCost;
    }
}