package com.badminton.booking.controller;

import com.badminton.booking.dto.BookingAddItemRequest;
import com.badminton.booking.dto.BookingReceipt;
import com.badminton.booking.dto.BookingRequest;
import com.badminton.booking.dto.SettlementRequest;
import com.badminton.booking.entity.Court;
import com.badminton.booking.entity.CourtPrice;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.Product;
import com.badminton.booking.entity.User;
import com.badminton.booking.entity.UserViolation;
import com.badminton.booking.entity.Visitor;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.CourtMaintenanceRepository;
import com.badminton.booking.repository.CourtPriceRepository;
import com.badminton.booking.repository.CourtRepository;
import com.badminton.booking.repository.NormalBookingRepository;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.repository.UserViolationRepository;
import com.badminton.booking.repository.VisitorRepository;
import com.badminton.booking.service.BookingBillService;
import com.badminton.booking.service.BookingInventoryService;
import com.badminton.booking.service.BookingSettlementService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

  @jakarta.persistence.PersistenceContext
  private jakarta.persistence.EntityManager em;

  private List<com.badminton.booking.entity.DailyVisitorSession> dailyVisitorSessions(
    Long courtId,
    LocalDate date
  ) {
    return em
      .createQuery(
        "select s from DailyVisitorSession s where s.schedule.court.id = :courtId and s.sessionDate = :date and s.status <> 'CANCELLED'",
        com.badminton.booking.entity.DailyVisitorSession.class
      )
      .setParameter("courtId", courtId)
      .setParameter("date", date)
      .getResultList();
  }

  private final com.badminton.booking.service.CustomerBookingPolicy customerBookingPolicy;
  private final NormalBookingRepository bookingRepository;
  private final UserRepository userRepository;
  private final CourtRepository courtRepository;
  private final UserViolationRepository violationRepository;
  private final VisitorRepository visitorRepository;
  private final CourtPriceRepository courtPriceRepository;
  private final BookingInventoryService bookingInventoryService;
  private final CourtMaintenanceRepository courtMaintenanceRepository;
  private final BookingSettlementService settlementService;
  private final BookingBillService bookingBillService;
  private final com.badminton.booking.service.CounterBookingVerificationService counterVerification;

  public BookingController(
    com.badminton.booking.service.CustomerBookingPolicy customerBookingPolicy,
    NormalBookingRepository bookingRepository,
    UserRepository userRepository,
    CourtRepository courtRepository,
    CourtMaintenanceRepository courtMaintenanceRepository,
    UserViolationRepository violationRepository,
    VisitorRepository visitorRepository,
    CourtPriceRepository courtPriceRepository,
    BookingInventoryService bookingInventoryService,
    BookingSettlementService settlementService,
    BookingBillService bookingBillService,
    com.badminton.booking.service.CounterBookingVerificationService counterVerification
  ) {
    this.customerBookingPolicy = customerBookingPolicy;
    this.bookingRepository = bookingRepository;
    this.userRepository = userRepository;
    this.courtRepository = courtRepository;
    this.courtMaintenanceRepository = courtMaintenanceRepository;
    this.violationRepository = violationRepository;
    this.visitorRepository = visitorRepository;
    this.courtPriceRepository = courtPriceRepository;
    this.bookingInventoryService = bookingInventoryService;
    this.settlementService = settlementService;
    this.bookingBillService = bookingBillService;
    this.counterVerification = counterVerification;
  }

  // CUSTOMER tự đặt sân. userId luôn được lấy từ JWT.
  @Transactional
  @PostMapping
  public List<NormalBooking> createBooking(
    @RequestBody BookingRequest request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    Long authenticatedUserId = Long.valueOf(jwt.getSubject());
    request.setUserId(authenticatedUserId);

    return createBookingInternal(request, null);
  }

  // STAFF/ADMIN tạo booking tại quầy. staffId được lấy từ JWT.
  @Transactional
  @PostMapping("/walk-in")
  public List<NormalBooking> createWalkInBooking(
    @RequestBody BookingRequest request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    if (jwt == null) throw new BusinessException(
      HttpStatus.UNAUTHORIZED,
      "Cần đăng nhập"
    );
    Long staffId = Long.valueOf(jwt.getSubject());

    User staff = userRepository
      .findById(staffId)
      .orElseThrow(() ->
        new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên")
      );

    if (!"STAFF".equals(staff.getRole()) && !"ADMIN".equals(staff.getRole())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,
        "Chỉ STAFF hoặc ADMIN được tạo booking tại quầy"
      );
    }

    if (request == null) throw new BusinessException(
      HttpStatus.BAD_REQUEST,
      "Thiếu dữ liệu booking"
    );
    request.setWalkInPhone(
      counterVerification.normalize(request.getWalkInPhone())
    );
    counterVerification.consume(
      request.getWalkInPhone(),
      request.getVerificationToken(),
      staffId
    );
    return createBookingInternal(request, staff);
  }

  private List<NormalBooking> createBookingInternal(
    BookingRequest request,
    User staff
  ) {
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

      user = userRepository
        .findById(request.getUserId())
        .orElseThrow(() ->
          new BusinessException(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy người dùng"
          )
        );

      // Phải xác minh số điện thoại trước khi đặt sân
      if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
        throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "Vui lòng xác minh số điện thoại trước khi đặt sân"
        );
      }

      if ("SUSPENDED".equals(user.getStatus())) {
        throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "Tài khoản của bạn đã bị khóa vì vi phạm nguyên tắc đặt sân. " +
            "Vui lòng liên hệ STAFF để biết thêm chi tiết."
        );
      }

      if ("WARNING".equals(user.getStatus())) {
        List<UserViolation> warningHistories =
          violationRepository.findWarningHistory(user.getId(), "WARNING");

        if (warningHistories.isEmpty()) {
          throw new BusinessException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Không tìm thấy lịch sử cảnh báo"
          );
        }

        UserViolation warningViolation = warningHistories.get(0);

        LocalDateTime warningUntil = warningViolation
          .getCreatedAt()
          .plusDays(2);

        if (LocalDateTime.now().isBefore(warningUntil)) {
          throw new BusinessException(
            HttpStatus.FORBIDDEN,
            "Tài khoản của bạn đang bị cảnh báo do không check-in sân. " +
              "Bạn bị tạm khóa quyền đặt sân trong 2 ngày. " +
              "Vui lòng thử lại sau khi thời gian cảnh báo kết thúc."
          );
        }
      }
    } else {
      if (
        request.getWalkInName() == null ||
        request.getWalkInName().isBlank() ||
        request.getWalkInPhone() == null ||
        request.getWalkInPhone().isBlank()
      ) {
        throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "Khách vãng lai phải có tên và số điện thoại"
        );
      }

      String name = request.getWalkInName().trim();

      String phone = request.getWalkInPhone().trim();

      visitor = visitorRepository.findByPhone(phone).orElseGet(() -> {
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

    if (request.getCourtIds() != null && !request.getCourtIds().isEmpty()) {
      courtIds.addAll(request.getCourtIds());
    } else if (request.getCourtId() != null) {
      courtIds.add(request.getCourtId());
    } else {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Vui lòng chọn ít nhất một sân"
      );
    }

    if (
      request.getBookingDate() == null ||
      request.getStartTime() == null ||
      request.getEndTime() == null
    ) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Ngày đặt, giờ bắt đầu và giờ kết thúc không được để trống"
      );
    }

    if (
      (request.getStartTime().getMinute() != 0 &&
        request.getStartTime().getMinute() != 30) ||
      (request.getEndTime().getMinute() != 0 &&
        request.getEndTime().getMinute() != 30) ||
      request.getStartTime().getSecond() != 0 ||
      request.getEndTime().getSecond() != 0 ||
      request.getStartTime().getNano() != 0 ||
      request.getEndTime().getNano() != 0
    ) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Chỉ được đặt sân theo khung giờ :00 hoặc :30"
      );
    }

    LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    if (
      request.getBookingDate().isBefore(today) ||
      request
        .getBookingDate()
        .isAfter(customerBookingPolicy.lastBookingDate(today))
    ) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Chỉ được đặt từ hôm nay đến " +
          customerBookingPolicy.lastBookingDate(today)
      );
    }

    LocalDateTime bookingNow = LocalDateTime.now(
      java.time.ZoneId.of("Asia/Ho_Chi_Minh")
    );
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

    // Kiểm tra ID sân.
    if (courtIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "ID sân không hợp lệ"
      );
    }

    // Không cho chọn cùng một sân nhiều lần.
    if (new java.util.HashSet<>(courtIds).size() != courtIds.size()) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Không được chọn trùng sân"
      );
    }

    // Các yêu cầu đặt nhiều sân đều khóa theo cùng thứ tự.
    courtIds.sort(Long::compareTo);

    List<Court> selectedCourts = new ArrayList<>();

    for (Long courtId : courtIds) {
      Court court = courtRepository
        .findByIdForBooking(courtId)
        .orElseThrow(() ->
          new BusinessException(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy sân ID: " + courtId
          )
        );

      if (!Boolean.TRUE.equals(court.getActive())) {
        throw new BusinessException(
          HttpStatus.CONFLICT,
          "Sân " + court.getName() + " đang ngừng hoạt động"
        );
      }

      // Giữ nguyên phần kiểm tra bảo trì và trùng lịch phía dưới.

      boolean maintenanceOverlap =
        courtMaintenanceRepository.existsActiveMaintenanceOverlap(
          courtId,
          bookingStart,
          bookingEnd
        );

      if (maintenanceOverlap) {
        throw new BusinessException(
          HttpStatus.CONFLICT,
          "Sân " +
            court.getName() +
            " đang bảo trì hoặc gặp sự cố trong khung giờ này"
        );
      }

      var dailySessions = dailyVisitorSessions(
        courtId,
        request.getBookingDate()
      );
      if (
        dailySessions
          .stream()
          .anyMatch(
            session ->
              request.getStartTime().isBefore(session.getEndTime()) &&
              request.getEndTime().isAfter(session.getStartTime())
          )
      ) {
        throw new BusinessException(
          HttpStatus.CONFLICT,
          "Sân đã có buổi Daily Visitor trong khung giờ này"
        );
      }
      List<NormalBooking> existingBookings =
        bookingRepository.findByCourtIdAndBookingDateAndStatusNot(
          courtId,
          request.getBookingDate(),
          "CANCELLED"
        );

      boolean overlap = existingBookings
        .stream()
        .filter(existing -> !"NO_SHOW".equals(existing.getStatus()))
        .anyMatch(
          existing ->
            request.getStartTime().isBefore(existing.getEndTime()) &&
            request.getEndTime().isAfter(existing.getStartTime())
        );

      if (overlap) {
        throw new BusinessException(
          HttpStatus.CONFLICT,
          "Sân " + court.getName() + " đã có người đặt trong khung giờ này"
        );
      }

      if (
        court.getRoom() == null ||
        !Boolean.TRUE.equals(court.getRoom().getActive()) ||
        court.getRoom().getCourtType() == null ||
        !Boolean.TRUE.equals(court.getRoom().getCourtType().getActive())
      ) {
        throw new BusinessException(
          HttpStatus.CONFLICT,
          "Phòng hoặc loại sân đang ngừng hoạt động"
        );
      }
      selectedCourts.add(court);
    }

    // =================================================
    // KIỂM TRA VÀ GIỮ ỐNG CẦU CHO BOOKING
    // =================================================

    int requestedTubes =
      request.getQuantityTubes() == null ? 0 : request.getQuantityTubes();
    int requestedPieces =
      request.getQuantityPieces() == null ? 0 : request.getQuantityPieces();
    if (
      requestedTubes < 0 ||
      requestedPieces < 0 ||
      requestedTubes > 10000 ||
      requestedPieces > 10000 ||
      (requestedTubes > 0 && requestedPieces > 0)
    ) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Chọn mua theo ống hoặc theo quả, số lượng hợp lệ"
      );
    }
    if (staff != null && requestedPieces > 0) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Tại quầy chỉ bán cầu theo ống trong booking"
      );
    }
    boolean hasShuttles = requestedTubes > 0 || requestedPieces > 0;
    if (hasShuttles != (request.getProductId() != null)) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Sản phẩm và số lượng cầu không hợp lệ"
      );
    }
    if (hasShuttles && selectedCourts.size() != 1) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Booking mua kèm cầu chỉ được chọn một sân"
      );
    }
    if (
      requestedPieces > 0 &&
      !customerBookingPolicy.allowsPieces(
        selectedCourts.get(0).getRoom().getCourtType().getName()
      )
    ) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Loại sân này không hỗ trợ mua cầu lẻ online"
      );
    }
    Product reservedProduct = null;
    Long shuttlecockAmount = 0L;
    if (hasShuttles) {
      reservedProduct =
        requestedPieces > 0
          ? bookingInventoryService.reservePieces(
              request.getProductId(),
              requestedPieces
            )
          : bookingInventoryService.reserveProduct(
              request.getProductId(),
              requestedTubes
            );
      shuttlecockAmount =
        (requestedPieces > 0
          ? reservedProduct.getPiecePrice()
          : reservedProduct.getTubePrice()) *
        (requestedPieces > 0 ? requestedPieces : requestedTubes);
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
      booking.setShuttlecockProduct(reservedProduct);

      booking.setShuttlecockQuantityTubes(requestedTubes);
      booking.setShuttlecockQuantityPieces(requestedPieces);

      booking.setShuttlecockUnitPrice(
        reservedProduct == null
          ? null
          : requestedPieces > 0
            ? reservedProduct.getPiecePrice()
            : reservedProduct.getTubePrice()
      );

      booking.setShuttlecockAmount(shuttlecockAmount);

      booking.setShuttlecockReservationActive(hasShuttles);

      booking.setShuttlecockIssued(false);

      CourtPrice courtPrice = courtPriceRepository
        .findByCourtTypeIdAndActiveTrue(court.getRoom().getCourtType().getId())
        .orElseThrow(() ->
          new BusinessException(
            HttpStatus.CONFLICT,
            "Loại sân này chưa được cấu hình giá"
          )
        );

      Long courtAmount = calculateTotalAmount(
        courtPrice,
        request.getStartTime(),
        request.getEndTime()
      );

      Long totalAmount = courtAmount + shuttlecockAmount;
      booking.setTotalAmount(totalAmount);
      bookings.add(booking);
    }

    List<NormalBooking> saved = bookingRepository.saveAll(bookings);
    // Booking tại quầy bắt đầu ngay được check-in và giao cầu trong cùng giao dịch.
    if ("CHECKED_IN".equals(initialStatus)) {
      for (NormalBooking booking : saved)
        bookingInventoryService.issueForBooking(booking);
    }
    return saved;
  }

  // CUSTOMER hủy booking của chính mình.
  @Transactional
  @DeleteMapping("/{id}")
  public NormalBooking cancelBooking(
    @PathVariable Long id,
    @AuthenticationPrincipal Jwt jwt
  ) {
    Long authenticatedUserId = Long.valueOf(jwt.getSubject());

    NormalBooking booking = bookingRepository
      .findByIdForSettlement(id)
      .orElseThrow(() ->
        new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy booking")
      );

    if (booking.getVisitor() != null) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,
        "Booking tại quầy phải do STAFF hoặc ADMIN hủy"
      );
    }

    if (
      booking.getUser() == null ||
      !authenticatedUserId.equals(booking.getUser().getId())
    ) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,
        "Bạn không có quyền hủy booking này"
      );
    }

    if ("CANCELLED".equals(booking.getStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, "Booking đã được hủy");
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

    if ("COMPLETED".equals(booking.getStatus())) {
      throw new BusinessException(
        HttpStatus.CONFLICT,
        "Booking đã hoàn thành nên không thể hủy"
      );
    }

    LocalDateTime now = LocalDateTime.now(
      java.time.ZoneId.of("Asia/Ho_Chi_Minh")
    );
    LocalDateTime bookingStart = LocalDateTime.of(
      booking.getBookingDate(),
      booking.getStartTime()
    );

    long minutesUntilStart = Duration.between(now, bookingStart).toMinutes();

    LocalDateTime cancellationDeadline = bookingStart.minusMinutes(30);

    if (!now.isBefore(cancellationDeadline)) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Không thể hủy booking khi còn 30 phút hoặc ít hơn đến giờ chơi"
      );
    }
    booking.setStatus("CANCELLED");
    booking.setCancelledAt(now);

    // Nếu booking đang giữ cầu thì trả lại số lượng có thể đặt
    bookingInventoryService.releaseReservation(booking);

    return bookingRepository.save(booking);
  }

  // STAFF/ADMIN hủy booking của khách tại quầy.
  @Transactional
  @DeleteMapping("/{id}/cancel-walk-in")
  public NormalBooking cancelWalkInBooking(
    @PathVariable Long id,
    @AuthenticationPrincipal Jwt jwt
  ) {
    if (jwt == null) throw new BusinessException(
      HttpStatus.UNAUTHORIZED,
      "Cần đăng nhập"
    );
    Long staffId = Long.valueOf(jwt.getSubject());

    User staff = userRepository
      .findById(staffId)
      .orElseThrow(() ->
        new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên")
      );

    if (!"STAFF".equals(staff.getRole()) && !"ADMIN".equals(staff.getRole())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,
        "Chỉ STAFF hoặc ADMIN mới có thể hủy booking tại quầy"
      );
    }

    NormalBooking booking = bookingRepository
      .findByIdForSettlement(id)
      .orElseThrow(() ->
        new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy booking")
      );

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

    if ("COMPLETED".equals(booking.getStatus())) {
      throw new BusinessException(
        HttpStatus.CONFLICT,
        "Booking đã hoàn thành nên không thể hủy"
      );
    }

    LocalDateTime now = LocalDateTime.now(
      java.time.ZoneId.of("Asia/Ho_Chi_Minh")
    );
    LocalDateTime bookingStart = LocalDateTime.of(
      booking.getBookingDate(),
      booking.getStartTime()
    );

    if (!bookingStart.isAfter(now.plusMinutes(30))) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Không được hủy trong 30 phút trước giờ chơi hoặc sau khi đã bắt đầu"
      );
    }

    booking.setStatus("CANCELLED");
    booking.setCancelledByStaffId(staff.getId());
    booking.setCancelledByStaffName(staff.getFullName());
    booking.setCancelledAt(now);
    bookingInventoryService.releaseReservation(booking);

    return bookingRepository.save(booking);
  }

  // STAFF/ADMIN check-in booking.
  @Transactional
  @PostMapping("/{id}/check-in")
  public NormalBooking checkInBooking(
    @PathVariable Long id,
    @AuthenticationPrincipal Jwt jwt
  ) {
    Long staffId = Long.valueOf(jwt.getSubject());

    User staff = userRepository
      .findById(staffId)
      .orElseThrow(() ->
        new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên")
      );

    if (!"STAFF".equals(staff.getRole()) && !"ADMIN".equals(staff.getRole())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,
        "Chỉ STAFF hoặc ADMIN mới được thực hiện check-in"
      );
    }

    if (!"ACTIVE".equals(staff.getStatus())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,
        "Tài khoản nhân viên không hoạt động"
      );
    }

    NormalBooking booking = bookingRepository
      .findByIdForSettlement(id)
      .orElseThrow(() ->
        new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy booking")
      );

    if ("CANCELLED".equals(booking.getStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, "Booking đã bị hủy");
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
    if ("COMPLETED".equals(booking.getStatus())) {
      throw new BusinessException(
        HttpStatus.CONFLICT,
        "Booking đã hoàn thành nên không thể check-in lại"
      );
    }

    if (!List.of("PENDING", "NO_SHOW_PENDING").contains(booking.getStatus())) {
      throw new BusinessException(
        HttpStatus.CONFLICT,
        "Trạng thái booking không cho phép check-in"
      );
    }

    LocalDateTime now = LocalDateTime.now(
      java.time.ZoneId.of("Asia/Ho_Chi_Minh")
    );
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

    /*
     * Nếu booking có đặt cầu:
     * - trừ tồn kho vật lý;
     * - giảm số lượng đang giữ;
     * - xuất theo FIFO;
     * - ghi inventory_issues;
     * - ghi inventory_issue_details.
     *
     * Nếu booking không đặt cầu thì method này không làm gì.
     */
    bookingInventoryService.issueForBooking(booking);

    return bookingRepository.save(booking);
  }

  // STAFF/ADMIN xem toàn bộ booking.
  @GetMapping
  public List<NormalBooking> getAllBookings() {
    return bookingRepository.findAll();
  }

  // CUSTOMER xem booking của chính mình.
  @GetMapping("/my")
  public List<NormalBooking> getMyBookings(@AuthenticationPrincipal Jwt jwt) {
    Long userId = Long.valueOf(jwt.getSubject());

    return bookingRepository.findByUser_IdOrderByBookingDateDescStartTimeDesc(
      userId
    );
  }

  private Long calculateTotalAmount(
    CourtPrice courtPrice,
    LocalTime startTime,
    LocalTime endTime
  ) {
    if (
      startTime.isBefore(courtPrice.getOpeningTime()) ||
      endTime.isAfter(courtPrice.getClosingTime())
    ) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Chỉ được đặt sân từ " +
          courtPrice.getOpeningTime() +
          " đến " +
          courtPrice.getClosingTime()
      );
    }

    LocalTime peakStart = courtPrice.getPeakStartTime();

    long normalMinutes = 0L;
    long peakMinutes = 0L;

    if (startTime.isBefore(peakStart)) {
      LocalTime normalEnd = endTime.isBefore(peakStart) ? endTime : peakStart;

      normalMinutes = Duration.between(startTime, normalEnd).toMinutes();
    }

    if (endTime.isAfter(peakStart)) {
      LocalTime actualPeakStart = startTime.isAfter(peakStart)
        ? startTime
        : peakStart;

      peakMinutes = Duration.between(actualPeakStart, endTime).toMinutes();
    }

    long normalAmount =
      (courtPrice.getNormalPricePerHour() * normalMinutes) / 60;

    long peakAmount = (courtPrice.getPeakPricePerHour() * peakMinutes) / 60;

    return normalAmount + peakAmount;
  }

  @PostMapping("/{id}/items")
  public BookingReceipt addBillItem(
    @PathVariable Long id,
    @RequestBody BookingAddItemRequest request
  ) {
    return bookingBillService.addItem(id, request);
  }

  @PostMapping("/{id}/items/{issueId}/cancel")
  public BookingReceipt cancelBillItem(
    @PathVariable Long id,
    @PathVariable Long issueId,
    @AuthenticationPrincipal Jwt jwt
  ) {
    return bookingBillService.cancelItem(
      id,
      issueId,
      Long.valueOf(jwt.getSubject())
    );
  }

  @GetMapping("/{id}/receipt")
  public BookingReceipt previewReceipt(
    @PathVariable Long id,
    @AuthenticationPrincipal Jwt jwt
  ) {
    User staff = userRepository
      .findById(Long.valueOf(jwt.getSubject()))
      .orElseThrow(() ->
        new BusinessException(HttpStatus.FORBIDDEN, "Không tìm thấy nhân viên")
      );
    if (
      !List.of("ADMIN", "STAFF").contains(staff.getRole())
    ) throw new BusinessException(
      HttpStatus.FORBIDDEN,
      "Chỉ Admin hoặc Staff được xem hóa đơn"
    );
    return settlementService.preview(id);
  }

  @PostMapping("/{id}/settle")
  public BookingReceipt settleBooking(
    @PathVariable Long id,
    @RequestBody SettlementRequest request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    return settlementService.settle(
      id,
      request,
      Long.valueOf(jwt.getSubject())
    );
  }

  @PostMapping("/{id}/complete")
  public BookingReceipt completeBooking(
    @PathVariable Long id,
    @AuthenticationPrincipal Jwt jwt
  ) {
    return settlementService.complete(id, Long.valueOf(jwt.getSubject()));
  }
}
