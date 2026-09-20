package com.badminton.booking.repository;

import com.badminton.booking.entity.CourtReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourtReviewImageRepository
        extends JpaRepository<CourtReviewImage, Long> {

    // Lấy toàn bộ ảnh thuộc một review
    List<CourtReviewImage> findByReview_Id(Long reviewId);
}