package com.badminton.booking.controller;

import com.badminton.booking.dto.BookingRequest;
import com.badminton.booking.entity.Court;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.User;
import com.badminton.booking.repository.CourtRepository;
import com.badminton.booking.repository.NormalBookingRepository;
import com.badminton.booking.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import com.badminton.booking.entity.UserViolation;
import com.badminton.booking.repository.UserViolationRepository;


import java.time.LocalDateTime;

import java.util.List;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final NormalBookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final UserViolationRepository violationRepository;

    public BookingController(
            NormalBookingRepository bookingRepository,
            UserRepository userRepository,
            CourtRepository courtRepository,
            UserViolationRepository violationRepository) {

        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.courtRepository = courtRepository;
        this.violationRepository = violationRepository;
    }
        @PostMapping
        public NormalBooking createBooking(@RequestBody BookingRequest request) {

        // 1. Kiểm tra người dùng
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new RuntimeException("Không tìm thấy người dùng"));

        // Tài khoản đã bị khóa
        if ("SUSPENDED".equals(user.getStatus())) {
                throw new RuntimeException(
                "Tài khoản của bạn đã bị khóa vì vi phạm nguyên tắc đặt sân. " +
                "Vui lòng liên hệ STAFF để biết thêm chi tiết."
        );
        }

        // Tài khoản đang bị cảnh báo
        if ("WARNING".equals(user.getStatus())) {

        List<UserViolation> warningHistories =
        violationRepository.findWarningHistory(
                user.getId(),
                "WARNING"
        );

        System.out.println("USER ID = " + user.getId());
        System.out.println("USER STATUS = " + user.getStatus());
        System.out.println("WARNING FOUND = " + warningHistories.size());

        if (warningHistories.isEmpty()) {
        throw new RuntimeException(
                "Không tìm thấy lịch sử cảnh báo"
        );
        }

        UserViolation warningViolation = warningHistories.get(0);

        LocalDateTime warningUntil =
                warningViolation.getCreatedAt().plusDays(2);

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(warningUntil)) {
            throw new RuntimeException(
                    "Tài khoản của bạn đang bị cảnh báo do không check-in sân. " +
                    "Bạn bị tạm khóa quyền đặt sân trong 2 ngày. " +
                    "Vui lòng thử lại sau khi thời gian cảnh báo kết thúc."
            );
        }
        }

        // 2. Kiểm tra sân
        Court court = courtRepository.findById(request.getCourtId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sân"));

        // 3. Kiểm tra thời gian hợp lệ
        // Chỉ cho bắt đầu và kết thúc ở phút :00 hoặc :30
        if ((request.getStartTime().getMinute() != 0
                && request.getStartTime().getMinute() != 30)
                || (request.getEndTime().getMinute() != 0
                && request.getEndTime().getMinute() != 30)
                || request.getStartTime().getSecond() != 0
                || request.getEndTime().getSecond() != 0) {
        throw new RuntimeException(
                "Chỉ được đặt sân theo khung giờ :00 hoặc :30"
        );
        }
        // Kiểm tra khoảng ngày được phép đặt sân
        LocalDate today = LocalDate.now();

        if (request.getBookingDate().isBefore(today)) {
            throw new RuntimeException(" Đa co ngươi dat vao khung gio này, nen dat khung gio khac");
        }

        LocalDate thisSunday = today.with(
                TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)
        );

        LocalDate bookingEndDate;

        if (today.getDayOfWeek() == DayOfWeek.MONDAY) {
            bookingEndDate = thisSunday;
        } else {
            bookingEndDate = thisSunday.plusWeeks(1);
        }

        if (request.getBookingDate().isAfter(bookingEndDate)) {
            throw new RuntimeException(
                    "Chỉ được đặt sân đến hết Chủ nhật của tuần kế tiếp"
            );
        }
        long minutes = java.time.Duration.between(
        request.getStartTime(),
        request.getEndTime()
        ).toMinutes();

        if (minutes < 60) {
            throw new RuntimeException("Thời gian đặt sân tối thiểu là 1 giờ");
        }
        if (minutes % 30 != 0) {
        throw new RuntimeException(
                "Thời gian đặt sân phải tăng theo từng 30 phút"
        );
        }

        // 4. Kiểm tra các booking hiện tại của sân trong ngày đó
        List<NormalBooking> existingBookings =
                bookingRepository.findByCourtIdAndBookingDateAndStatusNot(
                        request.getCourtId(),
                        request.getBookingDate(),
                        "CANCELLED"
                );

        // 5. Kiểm tra có bị trùng thời gian hay không
        boolean overlap = existingBookings.stream()
                .filter(existing -> !"NO_SHOW".equals(existing.getStatus()))
                .anyMatch(existing ->
                        request.getStartTime().isBefore(existing.getEndTime()) &&
                        request.getEndTime().isAfter(existing.getStartTime())
                );

        if (overlap) {
            throw new RuntimeException(
                    "Sân đã được đặt trong khoảng thời gian này"
            );
        }

        // 6. Tạo booking mới
        NormalBooking booking = new NormalBooking();

        booking.setUser(user);
        booking.setCourt(court);
        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setStatus("PENDING");

        return bookingRepository.save(booking);
    }
    @DeleteMapping("/{id}")
    public NormalBooking cancelBooking(@PathVariable Long id) {

        // 1. Tìm booking
        NormalBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));

        // 2. Kiểm tra trạng thái
        if ("CANCELLED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking đã được hủy");
        }

        // 3. Lấy thời gian hiện tại
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        java.time.LocalDateTime bookingStart = java.time.LocalDateTime.of(
                booking.getBookingDate(),
                booking.getStartTime()
        );

        // 4. Phải hủy trước ít nhất 2 tiếng
        long minutesUntilStart =
                java.time.Duration.between(now, bookingStart).toMinutes();

        if (minutesUntilStart < 120) {
            throw new RuntimeException(
                    "Chỉ được hủy sân trước ít nhất 2 tiếng"
            );
        }

        // 5. Chuyển trạng thái thành CANCELLED
        booking.setStatus("CANCELLED");

        return bookingRepository.save(booking);
    }
    @PostMapping("/{id}/check-in")
    public NormalBooking checkInBooking(@PathVariable Long id, @RequestParam Long staffId) {
    
    User staff = userRepository.findById(staffId)
        .orElseThrow(() ->
                new RuntimeException("Không tìm thấy nhân viên"));

    if (!"STAFF".equals(staff.getRole())
        && !"ADMIN".equals(staff.getRole())) {
    throw new RuntimeException(
            "Chỉ nhân viên mới được thực hiện check-in"
    );
    }

    NormalBooking booking = bookingRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));

    if ("CANCELLED".equals(booking.getStatus())) {
        throw new RuntimeException("Booking đã bị hủy");
    }

    if ("NO_SHOW".equals(booking.getStatus())) {
        throw new RuntimeException("Booking đã được ghi nhận là không đến");
    }

    if ("CHECKED_IN".equals(booking.getStatus())) {
        throw new RuntimeException("Booking đã được check-in");
    }

    java.time.LocalDateTime now = java.time.LocalDateTime.now();

    java.time.LocalDateTime bookingStart =
            java.time.LocalDateTime.of(
                    booking.getBookingDate(),
                    booking.getStartTime()
            );

    java.time.LocalDateTime finalCheckInDeadline =
                bookingStart.plusMinutes(30);

    if (now.isBefore(bookingStart)) {
        throw new RuntimeException("Chưa đến giờ nhận sân");
    }

    if (!now.isBefore(finalCheckInDeadline)) {
        throw new RuntimeException("Đã quá 30 phút nhận sân");
    }

    booking.setStatus("CHECKED_IN");
    booking.setCheckedInAt(now);
    booking.setCheckedInBy(staff.getId());

    return bookingRepository.save(booking);
    }
    @GetMapping
    public List<NormalBooking> getAllBookings() {
        return bookingRepository.findAll();
    }
}