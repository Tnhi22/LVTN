package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "court_maintenances")
public class CourtMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sân nào đang/đã được bảo trì
    @ManyToOne
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    // EMERGENCY: sự cố đột xuất
    // SCHEDULED: bảo trì có kế hoạch
    @Column(nullable = false)
    private String type;

    // Lý do bảo trì
    @Column(nullable = false)
    private String reason;

    // Thời gian bắt đầu bảo trì
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    // Thời gian dự kiến kết thúc
    @Column(name = "end_time")
    private LocalDateTime endTime;

    // SCHEDULED / IN_PROGRESS / COMPLETED / CANCELLED
    @Column(nullable = false)
    private String status;

    // Chi phí bảo trì
    @Column(name = "maintenance_cost")
    private Long maintenanceCost;

    // STAFF/ADMIN tạo yêu cầu bảo trì
    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    // Thời điểm tạo bản ghi
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Thời điểm thực tế hoàn thành bảo trì
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public CourtMaintenance() {
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

    public Court getCourt() {
        return court;
    }

    public void setCourt(Court court) {
        this.court = court;
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

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getMaintenanceCost() {
        return maintenanceCost;
    }

    public void setMaintenanceCost(Long maintenanceCost) {
        this.maintenanceCost = maintenanceCost;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}