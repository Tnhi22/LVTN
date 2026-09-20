package com.badminton.booking.repository;

import com.badminton.booking.entity.CourtReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourtReviewRepository
        extends JpaRepository<CourtReview, Long> {

    // Kiểm tra booking này đã được đánh giá chưa
    boolean existsByBooking_Id(Long bookingId);

    // Lấy danh sách đánh giá của một sân, mới nhất trước
    List<CourtReview> findByCourt_IdOrderByCreatedAtDesc(Long courtId);
}