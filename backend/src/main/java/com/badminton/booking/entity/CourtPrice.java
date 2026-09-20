package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "court_prices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_court_prices_court_type",
                        columnNames = "court_type_id"
                )
        }
)
public class CourtPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(
            name = "court_type_id",
            nullable = false,
            unique = true
    )
    private CourtType courtType;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime = LocalTime.of(6, 0);

    @Column(name = "peak_start_time", nullable = false)
    private LocalTime peakStartTime = LocalTime.of(17, 0);

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime = LocalTime.of(22, 0);

    @Column(name = "normal_price_per_hour", nullable = false)
    private Long normalPricePerHour;

    @Column(name = "peak_price_per_hour", nullable = false)
    private Long peakPricePerHour;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public CourtPrice() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public CourtType getCourtType() {
        return courtType;
    }

    public void setCourtType(CourtType courtType) {
        this.courtType = courtType;
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public void setOpeningTime(LocalTime openingTime) {
        this.openingTime = openingTime;
    }

    public LocalTime getPeakStartTime() {
        return peakStartTime;
    }

    public void setPeakStartTime(LocalTime peakStartTime) {
        this.peakStartTime = peakStartTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public void setClosingTime(LocalTime closingTime) {
        this.closingTime = closingTime;
    }

    public Long getNormalPricePerHour() {
        return normalPricePerHour;
    }

    public void setNormalPricePerHour(Long normalPricePerHour) {
        this.normalPricePerHour = normalPricePerHour;
    }

    public Long getPeakPricePerHour() {
        return peakPricePerHour;
    }

    public void setPeakPricePerHour(Long peakPricePerHour) {
        this.peakPricePerHour = peakPricePerHour;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
