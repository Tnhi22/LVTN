package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "daily_visitor_participants")
public class DailyVisitorParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private DailyVisitorSession session;

    // Có thể null nếu là khách tại quầy
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Tên người chơi thực tế
    @Column(name = "participant_name", nullable = false)
    private String participantName;

    // Người đại diện/chịu trách nhiệm cho nhóm
    @Column(name = "is_representative", nullable = false)
    private Boolean representative = false;

    @Column(nullable = false)
    private String status = "CONFIRMED";

    @Column(nullable = false)
    private LocalDateTime registeredAt;

    private LocalDateTime checkedInAt;

    private Long checkedInBy;

    @Column(name = "representative_phone")
    private String representativePhone;

    @Column(name = "slot_count", nullable = false)
    private Integer slotCount = 1;


    @Column(name = "checked_in_slots", nullable = false)
    private Integer checkedInSlots = 0;

    public Integer getCheckedInSlots() {
    return checkedInSlots;
    }

    public void setCheckedInSlots(Integer checkedInSlots) {
        this.checkedInSlots = checkedInSlots;
    }
    public String getRepresentativePhone() {
    return representativePhone;
    }

    public void setRepresentativePhone(String representativePhone) {
        this.representativePhone = representativePhone;
    }

    public Integer getSlotCount() {
        return slotCount;
    }

    public void setSlotCount(Integer slotCount) {
        this.slotCount = slotCount;
    }

    public DailyVisitorParticipant() {
    }

    @PrePersist
    protected void onCreate() {
        registeredAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DailyVisitorSession getSession() {
        return session;
    }

    public void setSession(DailyVisitorSession session) {
        this.session = session;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getParticipantName() {
        return participantName;
    }

    public void setParticipantName(String participantName) {
        this.participantName = participantName;
    }

    public Boolean getRepresentative() {
        return representative;
    }

    public void setRepresentative(Boolean representative) {
        this.representative = representative;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
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
}