package com.badminton.booking.controller;

import com.badminton.booking.dto.BookingRequest;
import com.badminton.booking.entity.Court;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.User;
import com.badminton.booking.entity.UserViolation;
import com.badminton.booking.entity.Visitor;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.CourtRepository;
import com.badminton.booking.repository.NormalBookingRepository;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.repository.UserViolationRepository;
import com.badminton.booking.repository.VisitorRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    // CUSTOMER tự đặt sân. userId luôn được lấy từ JWT.
    @PostMapping
    public List<NormalBooking> createBooking(
            @RequestBody BookingRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Long authenticatedUserId = Long.valueOf(jwt.getSubject());
        request.setUserId(authenticatedUserId);

        return createBookingInternal(request, null);
    }

    // STAFF/ADMIN tạo booking tại quầy. staffId được lấy từ JWT.
    @PostMapping("/walk-in")
    public List<NormalBooking> createWalkInBooking(
            @RequestBody BookingRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Long staffId = Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhân viên"
                ));

        if (!"STAFF".equals(staff.getRole())
                && !"ADMIN".equals(staff.getRole())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ STAFF hoặc ADMIN được tạo booking tại quầy"
            );
        }

        return createBookingInternal(request, staff);
    }

    private List<NormalBooking> createBookingInternal(
            BookingRequest request,
            User staff) {

        if (request == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Dữ liệu đặt sân không được để trống"
            );
        }

        User user = null;
        Visitor visitor = null;

        if (staff == null) {
            if (request.getUserId() == null) {
                throw new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "Không xác định được khách hàng đăng nhập"
                );
            }

            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy người dùng"
                    ));

            if ("SUSPENDED".equals(user.getStatus())) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản của bạn đã bị khóa vì vi phạm nguyên tắc đặt sân. "
                                + "Vui lòng liên hệ STAFF để biết thêm chi tiết."
                );
            }

            if ("WARNING".equals(user.getStatus())) {
                List<UserViolation> warningHistories =
                        violationRepository.findWarningHistory(
                                user.getId(),
                                "WARNING"
                        );

                if (warningHistories.isEmpty()) {
                    throw new BusinessException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Không tìm thấy lịch sử cảnh báo"
                    );
                }

                UserViolation warningViolation = warningHistories.get(0);
                LocalDateTime warningUntil =
                        warningViolation.getCreatedAt().plusDays(2);

                if (LocalDateTime.now().isBefore(warningUntil)) {
                    throw new BusinessException(
                            HttpStatus.FORBIDDEN,
                            "Tài khoản của bạn đang bị cảnh báo do không check-in sân. "
                                    + "Bạn bị tạm khóa quyền đặt sân trong 2 ngày. "
                                    + "Vui lòng thử lại sau khi thời gian cảnh báo kết thúc."
                    );
                }
            }
        } else {
            if (request.getWalkInName() == null
                    || request.getWalkInName().isBlank()
                    || request.getWalkInPhone() == null
                    || request.getWalkInPhone().isBlank()) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
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
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Số điện thoại này đã bị khóa do từng không đến nhận sân"
                );
            }
        }

        List<Long> courtIds = new ArrayList<>();

        if (request.getCourtIds() != null
                && !request.getCourtIds().isEmpty()) {
            courtIds.addAll(request.getCourtIds());
        } else if (request.getCourtId() != null) {
            courtIds.add(request.getCourtId());
        } else {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn ít nhất một sân"
            );
        }

        if (request.getBookingDate() == null
                || request.getStartTime() == null
                || request.getEndTime() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày đặt, giờ bắt đầu và giờ kết thúc không được để trống"
            );
        }

        if ((request.getStartTime().getMinute() != 0
                && request.getStartTime().getMinute() != 30)
                || (request.getEndTime().getMinute() != 0
                && request.getEndTime().getMinute() != 30)
                || request.getStartTime().getSecond() != 0
                || request.getEndTime().getSecond() != 0
                || request.getStartTime().getNano() != 0
                || request.getEndTime().getNano() != 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ được đặt sân theo khung giờ :00 hoặc :30"
            );
        }

        LocalDate today = LocalDate.now();

        if (request.getBookingDate().isBefore(today)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
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
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
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

        if (!bookingEnd.isAfter(bookingStart)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Giờ kết thúc phải sau giờ bắt đầu"
            );
        }

        long minutes = Duration.between(
                request.getStartTime(),
                request.getEndTime()
        ).toMinutes();

        if (minutes < 60) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Thời gian đặt sân tối thiểu là 1 giờ"
            );
        }

        if (minutes % 30 != 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Thời gian đặt sân phải tăng theo từng 30 phút"
            );
        }

        String initialStatus = "PENDING";
        LocalDateTime checkedInAt = null;
        Long checkedInBy = null;

        if (staff == null) {
            if (!bookingStart.isAfter(bookingNow)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Không được đặt sân vào giờ đã qua"
                );
            }
        } else if (!bookingStart.isAfter(bookingNow)) {
            if (!bookingNow.isBefore(bookingEnd)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Khung giờ này đã kết thúc"
                );
            }

            if (!bookingNow.isBefore(bookingStart.plusMinutes(30))) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Không được tạo booking tại quầy cho khung đã bắt đầu quá 30 phút"
                );
            }

            initialStatus = "CHECKED_IN";
            checkedInAt = bookingNow;
            checkedInBy = staff.getId();
        }

        List<Court> selectedCourts = new ArrayList<>();

        for (Long courtId : courtIds) {
            Court court = courtRepository.findById(courtId)
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND,
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
                            request.getStartTime().isBefore(existing.getEndTime())
                                    && request.getEndTime().isAfter(existing.getStartTime())
                    );

            if (overlap) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
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

    // CUSTOMER hủy booking của chính mình.
    @DeleteMapping("/{id}")
    public NormalBooking cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long authenticatedUserId = Long.valueOf(jwt.getSubject());

        NormalBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking"
                ));

        if (booking.getVisitor() != null) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Booking tại quầy phải do STAFF hoặc ADMIN hủy"
            );
        }

        if (booking.getUser() == null
                || !authenticatedUserId.equals(booking.getUser().getId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền hủy booking này"
            );
        }

        if ("CANCELLED".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking đã được hủy"
            );
        }

        if ("CHECKED_IN".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking đã check-in nên không thể hủy"
            );
        }

        if ("NO_SHOW".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking đã được ghi nhận NO_SHOW"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bookingStart = LocalDateTime.of(
                booking.getBookingDate(),
                booking.getStartTime()
        );

        long minutesUntilStart = Duration.between(now, bookingStart).toMinutes();

        if (minutesUntilStart < 120) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ được hủy sân trước ít nhất 2 tiếng"
            );
        }

        booking.setStatus("CANCELLED");
        return bookingRepository.save(booking);
    }

    // STAFF/ADMIN hủy booking của khách tại quầy.
    @DeleteMapping("/{id}/cancel-walk-in")
    public NormalBooking cancelWalkInBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long staffId = Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhân viên"
                ));

        if (!"STAFF".equals(staff.getRole())
                && !"ADMIN".equals(staff.getRole())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ STAFF hoặc ADMIN mới có thể hủy booking tại quầy"
            );
        }

        NormalBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking"
                ));

        if (booking.getVisitor() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Đây không phải booking của khách tại quầy"
            );
        }

        if ("CANCELLED".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking đã được hủy trước đó"
            );
        }

        if ("CHECKED_IN".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking đã check-in nên không thể hủy"
            );
        }

        if ("NO_SHOW".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking đã được ghi nhận NO_SHOW"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bookingStart = LocalDateTime.of(
                booking.getBookingDate(),
                booking.getStartTime()
        );

        long minutesUntilStart = Duration.between(now, bookingStart).toMinutes();

        if (minutesUntilStart < 120) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ được hủy sân trước ít nhất 2 tiếng"
            );
        }

        booking.setStatus("CANCELLED");
        booking.setCancelledByStaffId(staff.getId());
        booking.setCancelledByStaffName(staff.getFullName());
        booking.setCancelledAt(now);

        return bookingRepository.save(booking);
    }

    // STAFF/ADMIN check-in booking.
    @PostMapping("/{id}/check-in")
    public NormalBooking checkInBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long staffId = Long.valueOf(jwt.getSubject());

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhân viên"
                ));

        if (!"STAFF".equals(staff.getRole())
                && !"ADMIN".equals(staff.getRole())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ STAFF hoặc ADMIN mới được thực hiện check-in"
            );
        }

        NormalBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking"
                ));

        if ("CANCELLED".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking đã bị hủy"
            );
        }

        if ("NO_SHOW".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking đã được ghi nhận là không đến"
            );
        }

        if ("CHECKED_IN".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking đã được check-in"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bookingStart = LocalDateTime.of(
                booking.getBookingDate(),
                booking.getStartTime()
        );
        LocalDateTime finalCheckInDeadline = bookingStart.plusMinutes(30);

        if (now.isBefore(bookingStart)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Chưa đến giờ nhận sân"
            );
        }

        if (!now.isBefore(finalCheckInDeadline)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Đã quá 30 phút nhận sân"
            );
        }

        booking.setStatus("CHECKED_IN");
        booking.setCheckedInAt(now);
        booking.setCheckedInBy(staff.getId());

        return bookingRepository.save(booking);
    }

    // STAFF/ADMIN xem toàn bộ booking.
    @GetMapping
    public List<NormalBooking> getAllBookings() {
        return bookingRepository.findAll();
    }

    // CUSTOMER xem booking của chính mình.
    @GetMapping("/my")
    public List<NormalBooking> getMyBookings(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = Long.valueOf(jwt.getSubject());

        return bookingRepository
                .findByUser_IdOrderByBookingDateDescStartTimeDesc(userId);
    }
}
