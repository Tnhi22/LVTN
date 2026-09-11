package com.badminton.booking.controller;

import com.badminton.booking.dto.BookingRequest;
import com.badminton.booking.entity.Court;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.User;
import com.badminton.booking.repository.CourtRepository;
import com.badminton.booking.repository.NormalBookingRepository;
import com.badminton.booking.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

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

    public BookingController(
            NormalBookingRepository bookingRepository,
            UserRepository userRepository,
            CourtRepository courtRepository) {

        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.courtRepository = courtRepository;
    }

    @PostMapping
    public NormalBooking createBooking(@RequestBody BookingRequest request) {

        // 1. Kiểm tra người dùng
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // 2. Kiểm tra sân
        Court court = courtRepository.findById(request.getCourtId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sân"));

        // 3. Kiểm tra thời gian hợp lệ
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new RuntimeException("Giờ bắt đầu phải trước giờ kết thúc");
        }
        // Kiểm tra khoảng ngày được phép đặt sân
        LocalDate today = LocalDate.now();

        if (request.getBookingDate().isBefore(today)) {
            throw new RuntimeException("Không được đặt sân vào ngày đã qua");
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

        // 4. Kiểm tra các booking hiện tại của sân trong ngày đó
        List<NormalBooking> existingBookings =
                bookingRepository.findByCourtIdAndBookingDateAndStatusNot(
                        request.getCourtId(),
                        request.getBookingDate(),
                        "CANCELLED"
                );

        // 5. Kiểm tra có bị trùng thời gian hay không
        boolean overlap = existingBookings.stream().anyMatch(existing ->
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
    public NormalBooking checkInBooking(@PathVariable Long id) {

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

    java.time.LocalDateTime checkInDeadline =
            bookingStart.plusMinutes(30);

    if (now.isBefore(bookingStart)) {
        throw new RuntimeException(
                "Chưa đến giờ nhận sân"
        );
    }

    if (!now.isBefore(checkInDeadline)) {
        throw new RuntimeException(
                "Đã quá thời gian nhận sân 30 phút"
        );
    }

    booking.setStatus("CHECKED_IN");
    booking.setCheckedInAt(now);

    // Tạm thời chưa gắn tài khoản nhân viên,
    // nên checkedInBy để null.
    booking.setCheckedInBy(null);

    return bookingRepository.save(booking);
    }
    @GetMapping
    public List<NormalBooking> getAllBookings() {
        return bookingRepository.findAll();
    }
}