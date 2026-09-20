package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "court_reviews",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_court_review_booking",
                        columnNames = "booking_id"
                )
        }
)
public class CourtReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mỗi booking chỉ được review một lần
    @OneToOne
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private NormalBooking booking;

    // Người đánh giá
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Sân được đánh giá
    @ManyToOne
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    // Số sao từ 1 đến 5
    @Column(nullable = false)
    private Integer rating;

    // Nội dung bình luận
    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public CourtReview() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public NormalBooking getBooking() {
        return booking;
    }

    public void setBooking(NormalBooking booking) {
        this.booking = booking;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Court getCourt() {
        return court;
    }

    public void setCourt(Court court) {
        this.court = court;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}