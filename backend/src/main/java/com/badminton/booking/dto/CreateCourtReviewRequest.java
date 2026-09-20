package com.badminton.booking.dto;

public class CreateCourtReviewRequest {

    private Long bookingId;

    // 1 - 5 sao
    private Integer rating;

    // Nội dung đánh giá
    private String comment;

    public CreateCourtReviewRequest() {
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
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
}