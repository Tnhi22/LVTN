package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalTime;

@Entity
@Table(name = "daily_visitor_schedules")
public class DailyVisitorSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(nullable = false, length = 10)
    private String skillLevel;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private Integer minParticipants = 6;

    @Column(nullable = false)
    private Integer maxParticipants = 8;

    @Column(nullable = false)
    private Boolean active = true;

    public DailyVisitorSchedule() {
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

    public String getSkillLevel() {
        return skillLevel;
    }

    public void setSkillLevel(String skillLevel) {
        this.skillLevel = skillLevel;
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

    public Integer getMinParticipants() {
        return minParticipants;
    }

    public void setMinParticipants(Integer minParticipants) {
        this.minParticipants = minParticipants;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}