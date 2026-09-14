package com.badminton.booking.controller;

import com.badminton.booking.dto.BookingRequest;
import com.badminton.booking.entity.Court;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.User;
import com.badminton.booking.entity.UserViolation;
import com.badminton.booking.repository.CourtRepository;
import com.badminton.booking.repository.NormalBookingRepository;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.repository.UserViolationRepository;
import com.badminton.booking.entity.Visitor;
import com.badminton.booking.repository.VisitorRepository;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final NormalBookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final UserViolationRepository violationRepository;
    private final VisitorRepository visitorRepository;

public BookingController(
        NormalBookingRepository bookingRepository,
        UserRepository userRepository,
        CourtRepository courtRepository,
        UserViolationRepository violationRepository,
        VisitorRepository visitorRepository) {

        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.courtRepository = courtRepository;
        this.violationRepository = violationRepository;
        this.visitorRepository = visitorRepository;
     }

    // Khách tự đặt: booking chờ staff check-in.
        @PostMapping
        public List<NormalBooking> createBooking(
                @RequestBody BookingRequest request) {

        return createBookingInternal(request, null);
        }

    // Staff tạo tại quầy khi khách đã có mặt: booking được check-in ngay.
        @PostMapping("/walk-in")
        public List<NormalBooking> createWalkInBooking(
                @RequestBody BookingRequest request,
                @RequestParam Long staffId) {

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));

        if (!"STAFF".equals(staff.getRole())
                && !"ADMIN".equals(staff.getRole())) {
            throw new RuntimeException(
                    "Chỉ STAFF hoặc ADMIN được tạo booking tại quầy"
            );
        }

        return createBookingInternal(request, staff);
    }

        private List<NormalBooking> createBookingInternal(
                BookingRequest request,
                User staff) {

        // 1. Kiểm tra khách hàng.
                // 1. Xác định CUSTOMER hoặc VISITOR.
        User user = null;
        Visitor visitor = null;

        if (staff == null) {

        // CUSTOMER online
        if (request.getUserId() == null) {
                throw new RuntimeException(
                        "Khách hàng phải có userId"
                );
        }

        user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new RuntimeException("Không tìm thấy người dùng"));

        if ("SUSPENDED".equals(user.getStatus())) {
                throw new RuntimeException(
                        "Tài khoản của bạn đã bị khóa vì vi phạm nguyên tắc đặt sân. " +
                        "Vui lòng liên hệ STAFF để biết thêm chi tiết."
                );
        }

        if ("WARNING".equals(user.getStatus())) {

                List<UserViolation> warningHistories =
                        violationRepository.findWarningHistory(
                                user.getId(),
                                "WARNING"
                        );

                if (warningHistories.isEmpty()) {
                throw new RuntimeException(
                        "Không tìm thấy lịch sử cảnh báo"
                );
                }

                UserViolation warningViolation =
                        warningHistories.get(0);

                LocalDateTime warningUntil =
                        warningViolation.getCreatedAt().plusDays(2);

                if (LocalDateTime.now().isBefore(warningUntil)) {
                throw new RuntimeException(
                        "Tài khoản của bạn đang bị cảnh báo do không check-in sân. " +
                        "Bạn bị tạm khóa quyền đặt sân trong 2 ngày. " +
                        "Vui lòng thử lại sau khi thời gian cảnh báo kết thúc."
                );
                }
        }

        } else {

        // VISITOR - khách vãng lai
        if (request.getWalkInName() == null
                || request.getWalkInName().isBlank()
                || request.getWalkInPhone() == null
                || request.getWalkInPhone().isBlank()) {

                throw new RuntimeException(
                        "Khách vãng lai phải có tên và số điện thoại"
                );
        }

        String name = request.getWalkInName().trim();
        String phone = request.getWalkInPhone().trim();

        visitor = visitorRepository.findByPhone(phone)
                .orElseGet(() -> {
                        Visitor newVisitor = new Visitor();
                        newVisitor.setFullName(name);
                        newVisitor.setPhone(phone);
                        newVisitor.setStatus("ACTIVE");

                        return visitorRepository.save(newVisitor);
                });

        if ("BLOCKED".equals(visitor.getStatus())) {
                throw new RuntimeException(
                        "Số điện thoại này đã bị khóa do từng không đến nhận sân"
                );
        }
        }

        // 2. Kiểm tra sân.
        List<Long> courtIds = new ArrayList<>();

        if (request.getCourtIds() != null
                && !request.getCourtIds().isEmpty()) {

        courtIds.addAll(request.getCourtIds());

        } else if (request.getCourtId() != null) {

        courtIds.add(request.getCourtId());

        } else {
        throw new RuntimeException(
                "Vui lòng chọn ít nhất một sân"
        );
        }

        // 3. Kiểm tra khung giờ :00 hoặc :30.
        if ((request.getStartTime().getMinute() != 0
                && request.getStartTime().getMinute() != 30)
                || (request.getEndTime().getMinute() != 0
                && request.getEndTime().getMinute() != 30)
                || request.getStartTime().getSecond() != 0
                || request.getEndTime().getSecond() != 0
                || request.getStartTime().getNano() != 0
                || request.getEndTime().getNano() != 0) {
            throw new RuntimeException(
                    "Chỉ được đặt sân theo khung giờ :00 hoặc :30"
            );
        }

        LocalDate today = LocalDate.now();

        if (request.getBookingDate().isBefore(today)) {
            throw new RuntimeException(
                    "Không được đặt sân vào ngày đã qua"
            );
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

        LocalDateTime bookingNow = LocalDateTime.now();
        LocalDateTime bookingStart = LocalDateTime.of(
                request.getBookingDate(),
                request.getStartTime()
        );
        LocalDateTime bookingEnd = LocalDateTime.of(
                request.getBookingDate(),
                request.getEndTime()
        );

        String initialStatus = "PENDING";
        LocalDateTime checkedInAt = null;
        Long checkedInBy = null;

        if (staff == null) {

    // Khách tự đặt online
    if (!bookingStart.isAfter(bookingNow)) {
        throw new RuntimeException(
                "Không được đặt sân vào giờ đã qua"
        );
    }

    // Khách online đặt khung tương lai
    initialStatus = "PENDING";

} else {

    // Staff tạo booking cho khung đang diễn ra
    if (!bookingStart.isAfter(bookingNow)) {

        // Không được tạo nếu khung giờ đã kết thúc
        if (!bookingNow.isBefore(bookingEnd)) {
            throw new RuntimeException(
                    "Khung giờ này đã kết thúc"
            );
        }

        // Chỉ cho nhận sân trong 30 phút đầu
        if (!bookingNow.isBefore(
                bookingStart.plusMinutes(30))) {
            throw new RuntimeException(
                    "Không được tạo booking tại quầy cho khung đã bắt đầu quá 30 phút"
            );
        }

        // Khách có mặt tại quầy nên check-in ngay
        initialStatus = "CHECKED_IN";
        checkedInAt = bookingNow;
        checkedInBy = staff.getId();

        } else {

                // Staff đặt trước khung tương lai cho khách
                initialStatus = "PENDING";
        }
        }

        long minutes = Duration.between(
        request.getStartTime(),
        request.getEndTime()
        ).toMinutes();

        if (minutes < 60) {
            throw new RuntimeException(
                    "Thời gian đặt sân tối thiểu là 1 giờ"
            );
        }

        if (minutes % 30 != 0) {
            throw new RuntimeException(
                    "Thời gian đặt sân phải tăng theo từng 30 phút"
            );
        }

        // 4. Tìm các booking của cùng sân trong ngày, trừ CANCELLED.
        List<Court> selectedCourts = new ArrayList<>();

        for (Long courtId : courtIds) {

        Court court = courtRepository.findById(courtId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy sân ID: " + courtId
                        ));

        List<NormalBooking> existingBookings =
                bookingRepository.findByCourtIdAndBookingDateAndStatusNot(
                        courtId,
                        request.getBookingDate(),
                        "CANCELLED"
                );

        boolean overlap = existingBookings.stream()
                .filter(existing ->
                        !"NO_SHOW".equals(existing.getStatus()))
                .anyMatch(existing ->
                        request.getStartTime()
                                .isBefore(existing.getEndTime())
                                &&
                        request.getEndTime()
                                .isAfter(existing.getStartTime())
                );

        if (overlap) {
                throw new RuntimeException(
                        "Sân " + court.getName()
                                + " đã có người đặt trong khung giờ này"
                );
        }

        selectedCourts.add(court);
        }
        List<NormalBooking> bookings = new ArrayList<>();

        for (Court court : selectedCourts) {

        NormalBooking booking = new NormalBooking();

        booking.setUser(user);
        booking.setVisitor(visitor);
        booking.setCourt(court);

        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());

        booking.setStatus(initialStatus);
        booking.setCheckedInAt(checkedInAt);
        booking.setCheckedInBy(checkedInBy);

        bookings.add(booking);
        }

        return bookingRepository.saveAll(bookings);
            }


    @DeleteMapping("/{id}")
    public NormalBooking cancelBooking(@PathVariable Long id) {

        NormalBooking booking = bookingRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Không tìm thấy booking"));

        if ("CANCELLED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking đã được hủy");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bookingStart = LocalDateTime.of(
                booking.getBookingDate(),
                booking.getStartTime()
        );

        long minutesUntilStart =
                Duration.between(now, bookingStart).toMinutes();

        if (minutesUntilStart < 120) {
            throw new RuntimeException(
                    "Chỉ được hủy sân trước ít nhất 2 tiếng"
            );
        }

        booking.setStatus("CANCELLED");
        return bookingRepository.save(booking);
    }

    @PostMapping("/{id}/check-in")
    public NormalBooking checkInBooking(
            @PathVariable Long id,
            @RequestParam Long staffId) {

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
                .orElseThrow(() ->
                        new RuntimeException("Không tìm thấy booking"));

        if ("CANCELLED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking đã bị hủy");
        }

        if ("NO_SHOW".equals(booking.getStatus())) {
            throw new RuntimeException(
                    "Booking đã được ghi nhận là không đến"
            );
        }

        if ("CHECKED_IN".equals(booking.getStatus())) {
            throw new RuntimeException(
                    "Booking đã được check-in"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bookingStart = LocalDateTime.of(
                booking.getBookingDate(),
                booking.getStartTime()
        );
        LocalDateTime finalCheckInDeadline =
                bookingStart.plusMinutes(30);

        if (now.isBefore(bookingStart)) {
            throw new RuntimeException("Chưa đến giờ nhận sân");
        }

        if (!now.isBefore(finalCheckInDeadline)) {
            throw new RuntimeException(
                    "Đã quá 30 phút nhận sân"
            );
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