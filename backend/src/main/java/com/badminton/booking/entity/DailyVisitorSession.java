package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "daily_visitor_sessions")
public class DailyVisitorSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "schedule_id", nullable = false)
    private DailyVisitorSchedule schedule;

    @Column(nullable = false)
    private LocalDate sessionDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private Integer minParticipants;

    @Column(nullable = false)
    private Integer maxParticipants;

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "cancel_reason")
    private String cancelReason;

    // Loại cầu được sử dụng cho buổi Daily Visitor
    @ManyToOne
    @JoinColumn(
            name = "shuttlecock_product_id",
            nullable = false
    )
    private Product shuttlecockProduct;

    // Mỗi buổi sử dụng đúng 1 ống cầu
    @Column(
            name = "shuttlecock_quantity_tubes",
            nullable = false
    )
    private Integer shuttlecockQuantityTubes = 1;

    // Đánh dấu đã thực sự trừ kho hay chưa
    @Column(
            name = "shuttlecock_issued",
            nullable = false
    )
    private Boolean shuttlecockIssued = false;

    // Thời điểm xuất ống cầu khỏi kho
    @Column(name = "shuttlecock_issued_at")
    private LocalDateTime shuttlecockIssuedAt;

    public DailyVisitorSession() {
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = "OPEN";
        }

        if (shuttlecockQuantityTubes == null) {
            shuttlecockQuantityTubes = 1;
        }

        if (shuttlecockIssued == null) {
            shuttlecockIssued = false;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DailyVisitorSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(
            DailyVisitorSchedule schedule) {
        this.schedule = schedule;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(
            LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(
            LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(
            LocalTime endTime) {
        this.endTime = endTime;
    }

    public Integer getMinParticipants() {
        return minParticipants;
    }

    public void setMinParticipants(
            Integer minParticipants) {
        this.minParticipants = minParticipants;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(
            Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(
            String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Product getShuttlecockProduct() {
        return shuttlecockProduct;
    }

    public void setShuttlecockProduct(
            Product shuttlecockProduct) {
        this.shuttlecockProduct = shuttlecockProduct;
    }

    public Integer getShuttlecockQuantityTubes() {
        return shuttlecockQuantityTubes;
    }

    public void setShuttlecockQuantityTubes(
            Integer shuttlecockQuantityTubes) {
        this.shuttlecockQuantityTubes =
                shuttlecockQuantityTubes;
    }

    public Boolean getShuttlecockIssued() {
        return shuttlecockIssued;
    }

    public void setShuttlecockIssued(
            Boolean shuttlecockIssued) {
        this.shuttlecockIssued =
                shuttlecockIssued;
    }

    public LocalDateTime getShuttlecockIssuedAt() {
        return shuttlecockIssuedAt;
    }

    public void setShuttlecockIssuedAt(
            LocalDateTime shuttlecockIssuedAt) {
        this.shuttlecockIssuedAt =
                shuttlecockIssuedAt;
    }
}