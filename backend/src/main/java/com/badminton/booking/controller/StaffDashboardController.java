package com.badminton.booking.controller;

import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff/dashboard")
public class StaffDashboardController {

  private final NormalBookingRepository bookings;
  private final CourtRepository courts;
  private final CourtMaintenanceRepository maintenance;
  private final UserRepository users;
  private final jakarta.persistence.EntityManager entityManager;
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  public StaffDashboardController(
    NormalBookingRepository bookings,
    CourtRepository courts,
    CourtMaintenanceRepository maintenance,
    UserRepository users,
    jakarta.persistence.EntityManager entityManager
  ) {
    this.bookings = bookings;
    this.courts = courts;
    this.maintenance = maintenance;
    this.users = users;
    this.entityManager = entityManager;
  }

  public record BookingRow(
    Long id,
    Long courtId,
    String courtName,
    String customerName,
    String phone,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    String status,
    Long totalAmount,
    LocalDateTime checkedInAt,
    Long checkedInBy,
    boolean walkIn,
    boolean canCheckIn,
    String checkInHint
  ) {}

  public record CourtRow(Long id, String name, boolean active) {}

  public record MaintenanceRow(
    Long id,
    Long courtId,
    String reason,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String status,
    Long createdBy,
    String createdByName
  ) {}

  public record Summary(
    long total,
    long waiting,
    long playing,
    long completed
  ) {}

  public record MyStats(
    Long staffId,
    String staffName,
    long checkedInBookings,
    long completedBookings,
    long dailyCheckIns,
    long maintenanceReports,
    long bookingCash,
    long bookingBankTransfer,
    long dailyVisitorCash,
    long totalCollected
  ) {}

  public record Dashboard(
    LocalDate date,
    LocalDateTime serverTime,
    Summary summary,
    List<CourtRow> courts,
    List<BookingRow> bookings,
    List<MaintenanceRow> maintenance,
    MyStats myStats
  ) {}

  @GetMapping
  @Transactional(readOnly = true)
  public Dashboard get(
    @RequestParam(required = false) @DateTimeFormat(
      iso = DateTimeFormat.ISO.DATE
    ) LocalDate date,
    @AuthenticationPrincipal Jwt jwt
  ) {
    if (jwt == null) throw new BusinessException(
      HttpStatus.UNAUTHORIZED,
      "Vui lòng đăng nhập"
    );
    User staff = users
      .findById(Long.valueOf(jwt.getSubject()))
      .orElseThrow(() ->
        new BusinessException(
          HttpStatus.UNAUTHORIZED,
          "Không tìm thấy tài khoản"
        )
      );
    if (
      !List.of("STAFF", "ADMIN").contains(staff.getRole()) ||
      !"ACTIVE".equals(staff.getStatus())
    ) throw new BusinessException(
      HttpStatus.FORBIDDEN,
      "Chỉ nhân viên đang hoạt động được xem dashboard"
    );
    LocalDateTime now = LocalDateTime.now(ZONE);
    LocalDate day = date == null ? now.toLocalDate() : date;
    List<NormalBooking> list = bookings.findByBookingDate(day);
    List<BookingRow> rows = list
      .stream()
      .sorted(
        Comparator.comparing(NormalBooking::getStartTime).thenComparing(
          NormalBooking::getId
        )
      )
      .map(b -> row(b, now))
      .toList();
    long waiting = list
      .stream()
      .filter(b ->
        List.of("PENDING", "NO_SHOW_PENDING").contains(b.getStatus())
      )
      .count();
    long playing = list
      .stream()
      .filter(
        b ->
          "CHECKED_IN".equals(b.getStatus()) &&
          !now.isBefore(day.atTime(b.getStartTime())) &&
          now.isBefore(day.atTime(b.getEndTime()))
      )
      .count();
    long completed = list
      .stream()
      .filter(b -> "COMPLETED".equals(b.getStatus()))
      .count();
    var courtRows = courts
      .findAll()
      .stream()
      .sorted(Comparator.comparing(c -> c.getId()))
      .map(c ->
        new CourtRow(c.getId(), c.getName(), Boolean.TRUE.equals(c.getActive()))
      )
      .toList();
    var maintenanceRows = entityManager
      .createQuery(
        "SELECT m FROM CourtMaintenance m WHERE m.startTime < :end AND (m.endTime IS NULL OR m.endTime > :start) ORDER BY m.startTime",
        com.badminton.booking.entity.CourtMaintenance.class
      )
      .setParameter("start", day.atStartOfDay())
      .setParameter("end", day.plusDays(1).atStartOfDay())
      .getResultList()
      .stream()
      .filter(
        m ->
          m.getStartTime().isBefore(day.plusDays(1).atStartOfDay()) &&
          (m.getEndTime() == null || m.getEndTime().isAfter(day.atStartOfDay()))
      )
      .map(m ->
        new MaintenanceRow(
          m.getId(),
          m.getCourt().getId(),
          m.getReason(),
          m.getStartTime(),
          m.getEndTime(),
          m.getStatus(),
          m.getCreatedBy().getId(),
          m.getCreatedBy().getFullName()
        )
      )
      .toList();
    return new Dashboard(
      day,
      now,
      new Summary(rows.size(), waiting, playing, completed),
      courtRows,
      rows,
      maintenanceRows,
      stats(staff, day)
    );
  }

  private BookingRow row(NormalBooking b, LocalDateTime now) {
    LocalDateTime start = b.getBookingDate().atTime(b.getStartTime());
    boolean waiting = List.of("PENDING", "NO_SHOW_PENDING").contains(
      b.getStatus()
    );
    boolean can =
      waiting && !now.isBefore(start) && now.isBefore(start.plusMinutes(30));
    String hint = !waiting
      ? "Trạng thái không cho phép check-in"
      : now.isBefore(start)
        ? "Chưa đến giờ nhận sân"
        : !now.isBefore(start.plusMinutes(30))
          ? "Đã quá 30 phút nhận sân"
          : "Có thể check-in";
    String name =
      b.getUser() != null
        ? b.getUser().getFullName()
        : b.getVisitor() != null
          ? b.getVisitor().getFullName()
          : "Khách";
    String phone =
      b.getUser() != null
        ? b.getUser().getPhone()
        : b.getVisitor() != null
          ? b.getVisitor().getPhone()
          : null;
    return new BookingRow(
      b.getId(),
      b.getCourt().getId(),
      b.getCourt().getName(),
      name,
      phone,
      b.getBookingDate(),
      b.getStartTime(),
      b.getEndTime(),
      b.getStatus(),
      b.getTotalAmount(),
      b.getCheckedInAt(),
      b.getCheckedInBy(),
      b.getVisitor() != null,
      can,
      hint
    );
  }

  private long value(String query, Long staffId, LocalDate day) {
    return entityManager
      .createQuery(query, Long.class)
      .setParameter("staffId", staffId)
      .setParameter("start", day.atStartOfDay())
      .setParameter("end", day.plusDays(1).atStartOfDay())
      .getSingleResult();
  }

  private MyStats stats(User staff, LocalDate day) {
    Long id = staff.getId();
    long checked = value(
      "SELECT COUNT(b) FROM NormalBooking b WHERE b.checkedInBy = :staffId AND b.checkedInAt >= :start AND b.checkedInAt < :end",
      id,
      day
    );
    long completed = value(
      "SELECT COUNT(b) FROM NormalBooking b WHERE b.completedBy = :staffId AND b.completedAt >= :start AND b.completedAt < :end AND b.status = 'COMPLETED'",
      id,
      day
    );
    long daily = value(
      "SELECT COUNT(p) FROM DailyVisitorParticipant p WHERE p.checkedInBy = :staffId AND p.checkedInAt >= :start AND p.checkedInAt < :end",
      id,
      day
    );
    long reports = value(
      "SELECT COUNT(m) FROM CourtMaintenance m WHERE m.createdBy.id = :staffId AND m.createdAt >= :start AND m.createdAt < :end",
      id,
      day
    );
    String bookingSum =
      "SELECT COALESCE(SUM(b.totalAmount), 0) FROM NormalBooking b WHERE b.paidBy = :staffId AND b.paidAt >= :start AND b.paidAt < :end AND b.status = 'COMPLETED' AND b.paymentMethod = ";
    long cash = value(bookingSum + "'CASH'", id, day);
    long bank = value(bookingSum + "'BANK_TRANSFER'", id, day);
    long dailyCash = value(
      "SELECT COALESCE(SUM(p.amountPaid), 0) FROM DailyVisitorParticipant p WHERE p.paidBy = :staffId AND p.paidAt >= :start AND p.paidAt < :end",
      id,
      day
    );
    return new MyStats(
      id,
      staff.getFullName(),
      checked,
      completed,
      daily,
      reports,
      cash,
      bank,
      dailyCash,
      cash + bank + dailyCash
    );
  }
}
