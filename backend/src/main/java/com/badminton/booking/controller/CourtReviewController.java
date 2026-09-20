package com.badminton.booking.controller;

import com.badminton.booking.dto.CreateCourtReviewRequest;
import com.badminton.booking.entity.CourtReview;
import com.badminton.booking.service.CourtReviewService;

import com.badminton.booking.dto.UpdateCourtReviewRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api/court-reviews")
public class CourtReviewController {

    private final CourtReviewService courtReviewService;
    private final UserRepository userRepository;

    public CourtReviewController(
            CourtReviewService courtReviewService,
            UserRepository userRepository) {

        this.courtReviewService = courtReviewService;
        this.userRepository = userRepository;
    }

    // CUSTOMER tạo đánh giá sau khi booking đã COMPLETED
    @PostMapping
    public CourtReview createReview(
            @RequestBody CreateCourtReviewRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {

        Long userId = Long.valueOf(jwt.getSubject());

        return courtReviewService.createReview(userId, request);
    }

        @PutMapping("/{reviewId}")
    public CourtReview updateReview(
            @PathVariable Long reviewId,
            @RequestBody UpdateCourtReviewRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = Long.valueOf(jwt.getSubject());

        return courtReviewService.updateReview(
                reviewId,
                userId,
                request
        );
    }
    @DeleteMapping("/{reviewId}")
    public void deleteReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = Long.valueOf(jwt.getSubject());

        courtReviewService.deleteReview(
                reviewId,
                userId
        );
    }


    @GetMapping("/court/{courtId}")
    public java.util.List<CourtReview> getReviewsByCourt(
            @PathVariable Long courtId) {

        return courtReviewService.getReviewsByCourt(courtId);
    }


    @GetMapping("/admin")
    public List<CourtReview> getAllReviewsForAdmin(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = Long.valueOf(jwt.getSubject());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng"
                ));

        if (!"ADMIN".equals(user.getRole())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ ADMIN mới được xem toàn bộ đánh giá"
            );
        }

        return courtReviewService.getAllReviews();
    }


    @DeleteMapping("/{reviewId}/admin")
    public void deleteReviewByAdmin(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = Long.valueOf(jwt.getSubject());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng"
                ));

        if (!"ADMIN".equals(user.getRole())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ ADMIN mới được xóa đánh giá của khách hàng"
            );
        }

        courtReviewService.deleteReviewByAdmin(reviewId);
    }
}