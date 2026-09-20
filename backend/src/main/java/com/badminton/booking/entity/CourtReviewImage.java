package com.badminton.booking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "court_review_images")
public class CourtReviewImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ảnh thuộc review nào
    @ManyToOne
    @JoinColumn(name = "review_id", nullable = false)
    private CourtReview review;

    // Chỉ lưu đường dẫn/URL ảnh, không lưu file ảnh trực tiếp trong DB
    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    public CourtReviewImage() {
    }

    public Long getId() {
        return id;
    }

    public CourtReview getReview() {
        return review;
    }

    public void setReview(CourtReview review) {
        this.review = review;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}