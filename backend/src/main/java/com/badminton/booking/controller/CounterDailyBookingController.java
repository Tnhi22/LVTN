package com.badminton.booking.controller;

import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.service.CounterBookingVerificationService;
import com.badminton.booking.service.DailyVisitorFeePolicy;
import com.badminton.booking.service.DailyVisitorParticipantService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/counter-daily-bookings")
public class CounterDailyBookingController {

  @jakarta.persistence.PersistenceContext
  private jakarta.persistence.EntityManager em;

  private final UserRepository users;
  private final CounterBookingVerificationService verification;
  private final DailyVisitorParticipantService participants;
  private final DailyVisitorFeePolicy fees;

  public CounterDailyBookingController(
    UserRepository users,
    CounterBookingVerificationService verification,
    DailyVisitorParticipantService participants,
    DailyVisitorFeePolicy fees
  ) {
    this.users = users;
    this.verification = verification;
    this.participants = participants;
    this.fees = fees;
  }

  public record Request(
    Long sessionId,
    String fullName,
    String phone,
    Integer slotCount,
    String verificationToken
  ) {}

  public record Result(
    Long id,
    Long sessionId,
    Integer slotCount,
    long feePerPerson,
    Long amountDue,
    String status
  ) {}

  @PostMapping
  @Transactional
  public Result create(
    @RequestBody Request request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    if (jwt == null) throw new BusinessException(
      HttpStatus.UNAUTHORIZED,
      "Vui lòng đăng nhập"
    );
    Long staffId;
    try {
      staffId = Long.valueOf(jwt.getSubject());
    } catch (RuntimeException e) {
      throw new BusinessException(
        HttpStatus.UNAUTHORIZED,
        "Phiên đăng nhập không hợp lệ"
      );
    }
    var staff = users
      .findById(staffId)
      .orElseThrow(() ->
        new BusinessException(
          HttpStatus.UNAUTHORIZED,
          "Không tìm thấy tài khoản"
        )
      );
    if (
      !java.util.Set.of("ADMIN", "STAFF").contains(staff.getRole()) ||
      !"ACTIVE".equals(staff.getStatus())
    ) throw new BusinessException(
      HttpStatus.FORBIDDEN,
      "Chỉ Admin/Staff đang hoạt động được đăng ký khách tại quầy"
    );
    if (
      request == null ||
      request.slotCount() == null ||
      request.slotCount() < 1 ||
      request.slotCount() > 2
    ) throw new BusinessException(
      HttpStatus.BAD_REQUEST,
      "Mỗi lần đăng ký Daily tại quầy chỉ được 1–2 người"
    );
    String phone = verification.normalize(request.phone());
    verification.consume(phone, request.verificationToken(), staffId);
    DailyVisitorParticipant participant = participants.registerWalkIn(
      request.sessionId(),
      request.fullName(),
      phone,
      request.slotCount()
    );
    long fee = fees.feePerPerson(participant.getSession());
    participant.setAmountDue(
      Math.multiplyExact(fee, request.slotCount().longValue())
    );
    return new Result(
      participant.getId(),
      participant.getSession().getId(),
      participant.getSlotCount(),
      fee,
      participant.getAmountDue(),
      participant.getStatus()
    );
  }

  public record DailyRow(
    Long id,
    Long sessionId,
    java.time.LocalDate date,
    java.time.LocalTime startTime,
    java.time.LocalTime endTime,
    String courtName,
    String roomName,
    String skillLevel,
    String customerName,
    String phone,
    boolean walkIn,
    Integer slotCount,
    String status,
    String sessionStatus,
    String cancelReason,
    Long amountDue,
    Long amountPaid,
    java.time.LocalDateTime paidAt
  ) {}

  public record DailyList(
    java.time.LocalDateTime serverTime,
    java.util.List<DailyRow> rows
  ) {}

  @GetMapping
  @Transactional(readOnly = true)
  public DailyList list(
    @AuthenticationPrincipal Jwt jwt,
    @RequestParam @org.springframework.format.annotation.DateTimeFormat(
      iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE
    ) java.time.LocalDate from,
    @RequestParam @org.springframework.format.annotation.DateTimeFormat(
      iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE
    ) java.time.LocalDate to
  ) {
    if (jwt == null) throw new BusinessException(
      HttpStatus.UNAUTHORIZED,
      "Vui lòng đăng nhập"
    );
    var actor = users
      .findById(Long.valueOf(jwt.getSubject()))
      .orElseThrow(() ->
        new BusinessException(
          HttpStatus.UNAUTHORIZED,
          "Không tìm thấy tài khoản"
        )
      );
    if (
      !java.util.Set.of("ADMIN", "STAFF").contains(actor.getRole()) ||
      !"ACTIVE".equals(actor.getStatus())
    ) throw new BusinessException(
      HttpStatus.FORBIDDEN,
      "Chỉ Admin/Staff được quản lý Daily"
    );
    if (
      from.isAfter(to) ||
      java.time.temporal.ChronoUnit.DAYS.between(from, to) > 30
    ) throw new BusinessException(
      HttpStatus.BAD_REQUEST,
      "Chọn khoảng ngày tối đa 31 ngày"
    );
    var participants = em
      .createQuery(
        "select p from DailyVisitorParticipant p join fetch p.session s join fetch s.schedule d join fetch d.court c join fetch c.room r left join fetch p.user where s.sessionDate between :from and :to order by s.sessionDate desc, s.startTime, p.id desc",
        DailyVisitorParticipant.class
      )
      .setParameter("from", from)
      .setParameter("to", to)
      .getResultList();
    java.util.List<DailyRow> rows = new java.util.ArrayList<>();
    for (var p : participants) {
      var session = p.getSession();
      var schedule = session.getSchedule();
      var court = schedule.getCourt();
      var user = p.getUser();
      Long due = p.getAmountDue();
      if (due == null) {
        try {
          due = Math.multiplyExact(
            fees.feePerPerson(session),
            p.getSlotCount().longValue()
          );
        } catch (BusinessException ignored) {
          /* Chưa cấu hình giá: để trống, không đoán giá. */
        }
      }
      rows.add(
        new DailyRow(
          p.getId(),
          session.getId(),
          session.getSessionDate(),
          session.getStartTime(),
          session.getEndTime(),
          court.getName(),
          court.getRoom().getName(),
          schedule.getSkillLevel(),
          p.getParticipantName(),
          user == null ? p.getRepresentativePhone() : user.getPhone(),
          user == null,
          p.getSlotCount(),
          "CANCELLED".equals(session.getStatus()) ? "CANCELLED" : p.getStatus(),
          session.getStatus(),
          session.getCancelReason(),
          due,
          p.getAmountPaid(),
          p.getPaidAt()
        )
      );
    }
    return new DailyList(
      java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")),
      rows
    );
  }
}
