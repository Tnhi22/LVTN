package com.badminton.booking.service;

import com.badminton.booking.dto.CreateCourtReviewRequest;
import com.badminton.booking.entity.CourtReview;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.CourtReviewRepository;
import com.badminton.booking.repository.NormalBookingRepository;
import com.badminton.booking.repository.UserRepository;
import java.util.List;
import com.badminton.booking.dto.UpdateCourtReviewRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CourtReviewService {

    private final CourtReviewRepository courtReviewRepository;
    private final NormalBookingRepository bookingRepository;
    private final UserRepository userRepository;

    public CourtReviewService(
            CourtReviewRepository courtReviewRepository,
            NormalBookingRepository bookingRepository,
            UserRepository userRepository) {

        this.courtReviewRepository = courtReviewRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    public CourtReview createReview(
            Long userId,
            CreateCourtReviewRequest request) {

        // 1. Kiểm tra dữ liệu request
        if (request == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Dữ liệu đánh giá không được để trống"
            );
        }

        if (request.getBookingId() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Booking ID không được để trống"
            );
        }

        // 2. Rating chỉ được từ 1 đến 5
        if (request.getRating() == null
                || request.getRating() < 1
                || request.getRating() > 5) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Rating phải từ 1 đến 5 sao"
            );
        }

        // 3. Tìm user đang đăng nhập
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng"
                ));

        // 4. Tìm booking
        NormalBooking booking = bookingRepository
                .findById(request.getBookingId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking"
                ));

        // 5. Walk-in không được review bằng tài khoản CUSTOMER
        if (booking.getUser() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Booking walk-in không thể đánh giá bằng tài khoản này"
            );
        }

        // 6. Booking phải thuộc đúng user đang đăng nhập
        if (!booking.getUser().getId().equals(userId)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không thể đánh giá booking của người khác"
            );
        }

        // 7. Chỉ booking COMPLETED mới được đánh giá
        if (!"COMPLETED".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Chỉ booking đã hoàn thành mới có thể đánh giá"
            );
        }

        // 8. Mỗi booking chỉ được đánh giá một lần
        if (courtReviewRepository.existsByBooking_Id(booking.getId())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking này đã được đánh giá"
            );
        }

        // 9. Tạo review
        CourtReview review = new CourtReview();

        review.setBooking(booking);
        review.setUser(user);

        // Sân được lấy trực tiếp từ booking.
        // Client không được tự truyền courtId.
        review.setCourt(booking.getCourt());

        review.setRating(request.getRating());

        if (request.getComment() != null) {
            review.setComment(request.getComment().trim());
        }

        return courtReviewRepository.save(review);
    }

    public CourtReview updateReview(
        Long reviewId,
        Long userId,
        UpdateCourtReviewRequest request) {

    // 1. Kiểm tra request
    if (request == null) {
        throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "Dữ liệu đánh giá không được để trống"
        );
    }

        // 2. Tìm review
        CourtReview review = courtReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đánh giá"
                ));

        // 3. Chỉ chủ review mới được sửa
        if (review.getUser() == null
                || !review.getUser().getId().equals(userId)) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền sửa đánh giá này"
            );
        }

        // 4. Rating bắt buộc từ 1 đến 5
        if (request.getRating() == null
                || request.getRating() < 1
                || request.getRating() > 5) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Rating phải từ 1 đến 5 sao"
            );
        }

        // 5. Cập nhật
        review.setRating(request.getRating());

        if (request.getComment() != null) {
            review.setComment(request.getComment().trim());
        } else {
            review.setComment(null);
        }

        return courtReviewRepository.save(review);
    }


        public void deleteReview(
            Long reviewId,
            Long userId) {

        // 1. Tìm review
        CourtReview review = courtReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đánh giá"
                ));

        // 2. Chỉ chủ review mới được xóa
        if (review.getUser() == null
                || !review.getUser().getId().equals(userId)) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền xóa đánh giá này"
            );
        }

        // 3. Xóa review
        courtReviewRepository.delete(review);
    }
    public List<CourtReview> getReviewsByCourt(Long courtId) {

        return courtReviewRepository
                .findByCourt_IdOrderByCreatedAtDesc(courtId);
    }

    public List<CourtReview> getAllReviews() {
    return courtReviewRepository.findAll();
    }


    public void deleteReviewByAdmin(Long reviewId) {

        CourtReview review = courtReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đánh giá"
                ));

        courtReviewRepository.delete(review);
    }
}