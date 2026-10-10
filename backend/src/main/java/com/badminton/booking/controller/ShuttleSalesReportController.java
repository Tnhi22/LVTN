package com.badminton.booking.controller;

import com.badminton.booking.dto.BookingReceipt;
import com.badminton.booking.entity.*;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.*;
import com.badminton.booking.service.BookingSettlementService;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shuttle-sales-report")
public class ShuttleSalesReportController {

  @PersistenceContext
  private EntityManager em;

  private final UserRepository users;
  private final NormalBookingRepository bookings;
  private final BookingSettlementService settlement;

  public ShuttleSalesReportController(
    UserRepository users,
    NormalBookingRepository bookings,
    BookingSettlementService settlement
  ) {
    this.users = users;
    this.bookings = bookings;
    this.settlement = settlement;
  }

  private void staff(Jwt jwt) {
    if (jwt == null) throw new BusinessException(
      HttpStatus.UNAUTHORIZED,
      "Cần đăng nhập"
    );
    User user = users
      .findById(Long.valueOf(jwt.getSubject()))
      .orElseThrow(() ->
        new BusinessException(HttpStatus.FORBIDDEN, "Không tìm thấy tài khoản")
      );
    if (
      !List.of("ADMIN", "STAFF").contains(user.getRole())
    ) throw new BusinessException(
      HttpStatus.FORBIDDEN,
      "Chỉ Admin và Staff được xem thống kê"
    );
  }

  public record SaleRow(
    Long issueId,
    String issueCode,
    LocalDateTime issuedAt,
    Long productId,
    String productName,
    int quantityTubes,
    int quantityPieces,
    Long unitPrice,
    Long productAmount,
    Long bookingId,
    String courtName,
    String customerName,
    String customerPhone,
    String source
  ) {}

  public record ProductTotal(
    Long productId,
    String productName,
    long quantityTubes,
    long quantityPieces
  ) {}

  public record Report(
    LocalDate from,
    LocalDate to,
    long totalTubes,
    long totalPieces,
    List<ProductTotal> products,
    List<SaleRow> rows
  ) {}

  public record BookingDetail(
    Long id,
    String courtName,
    String roomName,
    String customerName,
    String phone,
    LocalDate bookingDate,
    LocalTime startTime,
    LocalTime endTime,
    String status,
    LocalDateTime paidAt,
    String paymentMethod,
    BookingReceipt receipt
  ) {}

  @GetMapping
  @Transactional(readOnly = true)
  public Report report(
    @AuthenticationPrincipal Jwt jwt,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    staff(jwt);
    if (
      from.isAfter(to) ||
      java.time.temporal.ChronoUnit.DAYS.between(from, to) > 30
    ) throw new BusinessException(
      HttpStatus.BAD_REQUEST,
      "Chọn khoảng ngày hợp lệ, tối đa 31 ngày"
    );
    List<InventoryIssue> issues = em
      .createQuery(
        """
        select i from InventoryIssue i join fetch i.product
        where i.issuedAt >= :start and i.issuedAt < :end
        and i.issueType in ('NORMAL_BOOKING','NORMAL_BOOKING_ADDON','COUNTER_SALE','COUNTER_SALE_PIECE')
        and i.cancelledAt is null and (i.quantityTubes > 0 or i.quantityPieces > 0)
        order by i.issuedAt desc, i.id desc
        """,
        InventoryIssue.class
      )
      .setParameter("start", from.atStartOfDay())
      .setParameter("end", to.plusDays(1).atStartOfDay())
      .getResultList();
    if (issues.isEmpty()) return new Report(
      from,
      to,
      0,
      0,
      List.of(),
      List.of()
    );
    List<Long> issueIds = issues.stream().map(InventoryIssue::getId).toList();
    Map<Long, Long> counterBooking = new HashMap<>();
    Map<Long, CounterSalePayment> counterPayments = new HashMap<>();
    for (CounterSalePayment payment : em
      .createQuery(
        "select p from CounterSalePayment p where p.issueId in :ids",
        CounterSalePayment.class
      )
      .setParameter("ids", issueIds)
      .getResultList()) {
      counterPayments.put(payment.getIssueId(), payment);
      if (payment.getBookingId() != null) counterBooking.put(
        payment.getIssueId(),
        payment.getBookingId()
      );
    }
    Set<Long> bookingIds = new HashSet<>(counterBooking.values());
    for (var issue : issues)
      if (
        !issue.getIssueType().startsWith("COUNTER_SALE") &&
        issue.getReferenceId() != null
      ) bookingIds.add(issue.getReferenceId());
    Map<Long, NormalBooking> byId = bookingIds.isEmpty()
      ? Map.of()
      : em
          .createQuery(
            "select b from NormalBooking b join fetch b.court where b.id in :ids",
            NormalBooking.class
          )
          .setParameter("ids", bookingIds)
          .getResultList()
          .stream()
          .collect(Collectors.toMap(NormalBooking::getId, b -> b));
    List<SaleRow> rows = new ArrayList<>();
    Map<Long, ProductTotal> totals = new LinkedHashMap<>();
    long count = 0,
      pieceCount = 0;
    for (var issue : issues) {
      String source = issue.getIssueType().startsWith("COUNTER_SALE")
        ? "COUNTER"
        : "NORMAL_BOOKING_ADDON".equals(issue.getIssueType())
          ? "ADDON"
          : "BOOKING";
      Long bookingId = "COUNTER".equals(source)
        ? counterBooking.get(issue.getId())
        : issue.getReferenceId();
      NormalBooking b = bookingId == null ? null : byId.get(bookingId);
      Product p = issue.getProduct();
      int quantity =
        issue.getQuantityTubes() == null ? 0 : issue.getQuantityTubes();
      int pieces =
        issue.getQuantityPieces() == null ? 0 : issue.getQuantityPieces();
      CounterSalePayment cp = counterPayments.get(issue.getId());
      String buyer =
        b != null
          ? customer(b)
          : cp != null && cp.getCustomerName() != null
            ? cp.getCustomerName()
            : "Khách mua tại quầy (chưa lưu tên)";
      String phone =
        b != null
          ? b.getUser() != null
            ? b.getUser().getPhone()
            : b.getVisitor() != null
              ? b.getVisitor().getPhone()
              : null
          : cp == null
            ? null
            : cp.getCustomerPhone();
      rows.add(
        new SaleRow(
          issue.getId(),
          issue.getIssueCode(),
          issue.getIssuedAt(),
          p.getId(),
          p.getName(),
          quantity,
          pieces,
          issue.getUnitPrice(),
          issue.getTotalAmount(),
          bookingId,
          b == null ? null : b.getCourt().getName(),
          buyer,
          phone,
          source
        )
      );
      ProductTotal old = totals.get(p.getId());
      totals.put(
        p.getId(),
        new ProductTotal(
          p.getId(),
          p.getName(),
          quantity + (old == null ? 0 : old.quantityTubes()),
          pieces + (old == null ? 0 : old.quantityPieces())
        )
      );
      count += quantity;
      pieceCount += pieces;
    }
    return new Report(
      from,
      to,
      count,
      pieceCount,
      totals
        .values()
        .stream()
        .sorted(
          Comparator.comparingLong(ProductTotal::quantityTubes).reversed()
        )
        .toList(),
      rows
    );
  }

  public record DailyUsageRow(
    Long issueId,
    String issueCode,
    LocalDateTime issuedAt,
    Long sessionId,
    LocalDate sessionDate,
    LocalTime startTime,
    LocalTime endTime,
    String skillLevel,
    String courtName,
    String sessionStatus,
    Long productId,
    String productName,
    int quantityTubes
  ) {}

  public record DailyUsage(
    long totalTubes,
    List<ProductTotal> products,
    List<DailyUsageRow> rows
  ) {}

  @GetMapping("/daily-usage")
  @Transactional(readOnly = true)
  public DailyUsage dailyUsage(
    @AuthenticationPrincipal Jwt jwt,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    staff(jwt);
    if (
      from.isAfter(to) ||
      java.time.temporal.ChronoUnit.DAYS.between(from, to) > 30
    ) throw new BusinessException(
      HttpStatus.BAD_REQUEST,
      "Chọn khoảng ngày hợp lệ, tối đa 31 ngày"
    );
    var issues = em
      .createQuery(
        "select i from InventoryIssue i join fetch i.product where i.issueType = 'DAILY_VISITOR' and i.cancelledAt is null and i.quantityTubes > 0 and i.issuedAt >= :start and i.issuedAt < :end order by i.issuedAt desc, i.id desc",
        InventoryIssue.class
      )
      .setParameter("start", from.atStartOfDay())
      .setParameter("end", to.plusDays(1).atStartOfDay())
      .getResultList();
    var ids = issues
      .stream()
      .map(InventoryIssue::getReferenceId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
    Map<Long, DailyVisitorSession> sessions = ids.isEmpty()
      ? Map.of()
      : em
          .createQuery(
            "select s from DailyVisitorSession s join fetch s.schedule ds join fetch ds.court where s.id in :ids",
            DailyVisitorSession.class
          )
          .setParameter("ids", ids)
          .getResultList()
          .stream()
          .collect(Collectors.toMap(DailyVisitorSession::getId, s -> s));
    List<DailyUsageRow> rows = new ArrayList<>();
    Map<Long, ProductTotal> totals = new LinkedHashMap<>();
    long count = 0;
    for (var issue : issues) {
      var session = sessions.get(issue.getReferenceId());
      var product = issue.getProduct();
      int quantity = issue.getQuantityTubes();
      rows.add(
        new DailyUsageRow(
          issue.getId(),
          issue.getIssueCode(),
          issue.getIssuedAt(),
          issue.getReferenceId(),
          session == null ? null : session.getSessionDate(),
          session == null ? null : session.getStartTime(),
          session == null ? null : session.getEndTime(),
          session == null ? null : session.getSchedule().getSkillLevel(),
          session == null ? null : session.getSchedule().getCourt().getName(),
          session == null ? null : session.getStatus(),
          product.getId(),
          product.getName(),
          quantity
        )
      );
      var old = totals.get(product.getId());
      totals.put(
        product.getId(),
        new ProductTotal(
          product.getId(),
          product.getName(),
          quantity + (old == null ? 0 : old.quantityTubes()),
          0
        )
      );
      count += quantity;
    }
    return new DailyUsage(count, new ArrayList<>(totals.values()), rows);
  }

  private String customer(NormalBooking b) {
    if (b == null) return "Khách mua tại quầy";
    if (b.getUser() != null) return b.getUser().getFullName();
    return b.getVisitor() == null
      ? "Khách tại quầy"
      : b.getVisitor().getFullName();
  }

  @GetMapping("/bookings/{id}")
  @Transactional(readOnly = true)
  public BookingDetail booking(
    @PathVariable Long id,
    @AuthenticationPrincipal Jwt jwt
  ) {
    staff(jwt);
    var b = bookings
      .findById(id)
      .orElseThrow(() ->
        new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy booking")
      );
    String phone =
      b.getUser() != null
        ? b.getUser().getPhone()
        : b.getVisitor() == null
          ? null
          : b.getVisitor().getPhone();
    return new BookingDetail(
      b.getId(),
      b.getCourt().getName(),
      b.getCourt().getRoom().getName(),
      customer(b),
      phone,
      b.getBookingDate(),
      b.getStartTime(),
      b.getEndTime(),
      b.getStatus(),
      b.getPaidAt(),
      b.getPaymentMethod(),
      settlement.preview(id)
    );
  }
}
