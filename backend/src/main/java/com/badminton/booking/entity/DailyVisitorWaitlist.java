package com.badminton.booking.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "daily_visitor_waitlists",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_waitlist_session_user",
                        columnNames = {"session_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_daily_waitlist_queue",
                        columnList = "session_id,status,created_at,id"
                )
        }
)
public class DailyVisitorWaitlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private DailyVisitorSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    // Dùng để xếp hàng; nếu trùng thời điểm thì so sánh ID.
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Thời điểm được mời nhận slot.
    @Column(name = "offered_at")
    private LocalDateTime offeredAt;

    // Hạn xác nhận: tối đa 5 phút, không vượt giờ bắt đầu.
    @Column(name = "offer_expires_at")
    private LocalDateTime offerExpiresAt;

    // Thời điểm xác nhận, từ chối, hủy hoặc hết hạn.
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // Đăng ký được tạo sau khi khách xác nhận lời mời.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", unique = true)
    private DailyVisitorParticipant participant;

    public DailyVisitorWaitlist() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
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

    public WaitlistStatus getStatus() {
        return status;
    }

    public void setStatus(WaitlistStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getOfferedAt() {
        return offeredAt;
    }

    public void setOfferedAt(LocalDateTime offeredAt) {
        this.offeredAt = offeredAt;
    }

    public LocalDateTime getOfferExpiresAt() {
        return offerExpiresAt;
    }

    public void setOfferExpiresAt(LocalDateTime offerExpiresAt) {
        this.offerExpiresAt = offerExpiresAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public DailyVisitorParticipant getParticipant() {
        return participant;
    }

    public void setParticipant(DailyVisitorParticipant participant) {
        this.participant = participant;
    }
}