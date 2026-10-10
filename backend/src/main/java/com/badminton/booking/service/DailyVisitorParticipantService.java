package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.User;
import com.badminton.booking.entity.Visitor;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.repository.VisitorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyVisitorParticipantService {

  private final DailyVisitorParticipantRepository participantRepository;

  private final UserRepository userRepository;

  private final DailyVisitorRegistrationGuard registrationGuard;

  private final VisitorRepository visitorRepository;

  private final DailyVisitorPaymentService paymentService;

  private final DailyVisitorInventoryService dailyVisitorInventoryService;

  private final DailyVisitorWaitlistService waitlistService;

  @PersistenceContext
  private EntityManager entityManager;

  public DailyVisitorParticipantService(
    DailyVisitorParticipantRepository participantRepository,
    DailyVisitorSessionRepository sessionRepository,
    UserRepository userRepository,
    VisitorRepository visitorRepository,
    DailyVisitorInventoryService dailyVisitorInventoryService,
    DailyVisitorPaymentService paymentService,
    DailyVisitorWaitlistService waitlistService,
    DailyVisitorRegistrationGuard registrationGuard
  ) {
    this.participantRepository = participantRepository;

    this.userRepository = userRepository;

    this.visitorRepository = visitorRepository;

    this.dailyVisitorInventoryService = dailyVisitorInventoryService;

    this.paymentService = paymentService;

    this.waitlistService = waitlistService;
    this.registrationGuard = registrationGuard;
  }

  // =========================================================

  // 1. CUSTOMER đăng ký online: một tài khoản = một slot

  // =========================================================

  @Transactional
  public DailyVisitorParticipant register(Long sessionId, Long userId) {
    validateSessionId(sessionId);

    if (userId == null) {
      throw new BusinessException(
        HttpStatus.UNAUTHORIZED,

        "Không xác định được người dùng đăng nhập"
      );
    }

    registrationGuard.lockAccount(userId);

    DailyVisitorSession session = waitlistService.lockSession(sessionId);

    User user = userRepository
      .findById(userId)

      .orElseThrow(() ->
        new BusinessException(
          HttpStatus.NOT_FOUND,

          "Không tìm thấy người dùng"
        )
      );

    validateCustomerCanRegister(user);

    // Ưu tiên người đang chờ và xử lý lời mời đã hết hạn.

    waitlistService.processSession(sessionId);

    validateSessionCanRegister(session);

    // Chỉ chặn đăng ký còn hiệu lực.

    // Đăng ký đã CANCELLED không ngăn khách đăng ký lại.

    boolean alreadyRegistered = participantRepository

      .findBySessionId(sessionId)

      .stream()

      .anyMatch(
        participant ->
          participant.getUser() != null &&
          userId.equals(participant.getUser().getId()) &&
          !"CANCELLED".equals(participant.getStatus())
      );

    if (alreadyRegistered) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Bạn đã đăng ký lượt chơi này"
      );
    }

    registrationGuard.requireNoOverlap(userId, session);

    // Bao gồm cả slot đang giữ cho người được mời.

    long remainingSlots = waitlistService.getAvailableSlots(session);

    if (remainingSlots < 1) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Buổi chơi không còn slot có thể đăng ký"
      );
    }

    DailyVisitorParticipant participant = new DailyVisitorParticipant();

    participant.setSession(session);

    participant.setUser(user);

    participant.setParticipantName(user.getFullName().trim());

    participant.setSlotCount(1);

    participant.setCheckedInSlots(0);

    participant.setStatus("CONFIRMED");

    DailyVisitorParticipant savedParticipant =
      participantRepository.saveAndFlush(participant);

    // Cập nhật OPEN/FULL theo đăng ký và slot giữ tạm.

    waitlistService.processSession(sessionId);

    return savedParticipant;
  }

  // =========================================================

  // 2. Đăng ký khách tại quầy

  // Quyền STAFF/ADMIN phải được bảo vệ tại API/SecurityConfig.

  // =========================================================

  @Transactional
  public DailyVisitorParticipant registerWalkIn(
    Long sessionId,
    String fullName,
    String phone,
    Integer slotCount
  ) {
    validateSessionId(sessionId);

    if (fullName == null || fullName.isBlank()) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Vui lòng nhập tên khách hàng"
      );
    }

    if (phone == null || phone.isBlank()) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Vui lòng nhập số điện thoại người đại diện"
      );
    }

    if (slotCount == null || slotCount < 1 || slotCount > 2) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Mỗi lần đăng ký Daily Visitor tại quầy chỉ được từ 1 đến 2 người"
      );
    }

    DailyVisitorSession session = waitlistService.lockSession(sessionId);

    waitlistService.processSession(sessionId);

    validateSessionCanRegister(session);

    long remainingSlots = waitlistService.getAvailableSlots(session);

    if (remainingSlots < 1) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Buổi chơi không còn slot có thể đăng ký"
      );
    }

    if (slotCount > remainingSlots) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Buổi chơi chỉ còn " + remainingSlots + " slot có thể đăng ký"
      );
    }

    String normalizedName = fullName.trim();

    String normalizedPhone = phone.trim();

    Visitor visitor = visitorRepository

      .findByPhone(normalizedPhone)

      .orElseGet(() -> {
        Visitor newVisitor = new Visitor();

        newVisitor.setFullName(normalizedName);

        newVisitor.setPhone(normalizedPhone);

        newVisitor.setStatus("ACTIVE");

        return visitorRepository.save(newVisitor);
      });

    if ("BLOCKED".equals(visitor.getStatus())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Số điện thoại này đã bị khóa do từng không đến nhận sân"
      );
    }

    DailyVisitorParticipant participant = new DailyVisitorParticipant();

    participant.setSession(session);

    participant.setUser(null);

    participant.setParticipantName(normalizedName);

    participant.setRepresentativePhone(normalizedPhone);

    participant.setSlotCount(slotCount);

    participant.setCheckedInSlots(0);

    participant.setStatus("CONFIRMED");

    DailyVisitorParticipant savedParticipant =
      participantRepository.saveAndFlush(participant);

    waitlistService.processSession(sessionId);

    return savedParticipant;
  }

  // =========================================================

  // 3. STAFF/ADMIN check-in

  // =========================================================

  @Transactional
  public DailyVisitorParticipant checkIn(Long participantId, Long staffId) {
    User staff = requireStaff(staffId);

    // Khóa session và đọc lại participant trước khi xử lý.

    DailyVisitorParticipant participant = findParticipantForUpdate(
      participantId
    );

    DailyVisitorSession session = participant.getSession();

    if (!"CONFIRMED".equals(participant.getStatus())) {
      String message;

      if ("CHECKED_IN".equals(participant.getStatus())) {
        message = "Người chơi đã được check-in trước đó";
      } else if ("NO_SHOW".equals(participant.getStatus())) {
        message = "Người chơi đã bị đánh dấu NO_SHOW";
      } else if ("CANCELLED".equals(participant.getStatus())) {
        message = "Lượt đăng ký đã bị hủy";
      } else {
        message = "Trạng thái hiện tại không cho phép check-in";
      }

      throw new BusinessException(
        HttpStatus.CONFLICT,

        message
      );
    }

    validateSessionCanBeCancelled(session);

    LocalDateTime now = LocalDateTime.now();

    LocalDateTime sessionStart = getSessionStart(session);

    LocalDateTime finalCheckInTime = sessionStart.plusMinutes(30);

    if (now.isBefore(sessionStart)) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Chưa đến thời gian check-in"
      );
    }

    if (!now.isBefore(finalCheckInTime)) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Đã quá thời gian check-in 30 phút"
      );
    }

    // Buổi đã bắt đầu: kết thúc các yêu cầu waiting.

    waitlistService.processSession(session.getId());

    participant.setCheckedInSlots(participant.getSlotCount());

    participant.setStatus("CHECKED_IN");

    participant.setCheckedInAt(now);

    participant.setCheckedInBy(staff.getId());

    DailyVisitorParticipant savedParticipant =
      participantRepository.saveAndFlush(participant);

    Long checkedInSlots = participantRepository.getCheckedInSlots(
      session.getId()
    );

    long actualCheckedInSlots = checkedInSlots == null ? 0L : checkedInSlots;

    // Giữ nguyên quy trình xuất cầu FIFO.

    if (
      actualCheckedInSlots >= session.getMinParticipants() &&
      !Boolean.TRUE.equals(session.getShuttlecockIssued())
    ) {
      dailyVisitorInventoryService.issueForSession(session);
    }

    // Giữ nguyên quy trình thu tiền tại check-in.

    paymentService.collectAtCheckIn(
      savedParticipant,

      session,

      staff.getId()
    );

    return savedParticipant;
  }

  // =========================================================

  // 4. CUSTOMER hủy đăng ký của chính mình

  // =========================================================

  @Transactional
  public DailyVisitorParticipant cancelByCustomer(
    Long participantId,
    Long userId
  ) {
    if (userId == null) {
      throw new BusinessException(
        HttpStatus.UNAUTHORIZED,

        "Không xác định được tài khoản đăng nhập"
      );
    }

    DailyVisitorParticipant participant = findParticipantForUpdate(
      participantId
    );

    if (participant.getUser() == null) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Khách tại quầy không thể tự hủy trên web"
      );
    }

    if (!userId.equals(participant.getUser().getId())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Bạn không có quyền hủy lượt đăng ký này"
      );
    }

    if (!"CUSTOMER".equals(participant.getUser().getRole())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Chỉ khách hàng mới có thể tự hủy lượt đăng ký"
      );
    }

    return cancelParticipant(participant);
  }

  // =========================================================

  // 5. STAFF/ADMIN hủy đăng ký khách tại quầy

  // =========================================================

  @Transactional
  public DailyVisitorParticipant cancelWalkInByStaff(
    Long participantId,
    Long staffId
  ) {
    requireStaff(staffId);

    DailyVisitorParticipant participant = findParticipantForUpdate(
      participantId
    );

    if (participant.getUser() != null) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Đây là khách có tài khoản, khách phải tự hủy trên web"
      );
    }

    return cancelParticipant(participant);
  }

  // =========================================================

  // Các hàm hỗ trợ

  // =========================================================

  private void validateSessionId(Long sessionId) {
    if (sessionId == null || sessionId <= 0) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Vui lòng chọn buổi chơi hợp lệ"
      );
    }
  }

  private void validateCustomerCanRegister(User user) {
    if (!"CUSTOMER".equals(user.getRole())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Chỉ khách hàng mới có thể tự đăng ký lượt chơi"
      );
    }

    if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Vui lòng xác minh số điện thoại trước khi đăng ký chơi"
      );
    }

    if ("SUSPENDED".equals(user.getStatus())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Tài khoản đã bị khóa nên không thể đăng ký lượt chơi"
      );
    }

    if (user.getFullName() == null || user.getFullName().isBlank()) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Vui lòng cập nhật họ tên trước khi đăng ký"
      );
    }
  }

  private void validateSessionCanRegister(DailyVisitorSession session) {
    if (
      !Boolean.TRUE.equals(session.getSchedule().getActive()) ||
      !Boolean.TRUE.equals(session.getSchedule().getCourt().getActive()) ||
      !Boolean.TRUE.equals(
        session.getSchedule().getCourt().getRoom().getActive()
      ) ||
      !Boolean.TRUE.equals(
        session.getSchedule().getCourt().getRoom().getCourtType().getActive()
      )
    ) {
      throw new BusinessException(
        HttpStatus.CONFLICT,
        "Sân hoặc lịch vãng lai đang ngừng hoạt động"
      );
    }

    if (!LocalDateTime.now().isBefore(getSessionStart(session))) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Buổi chơi đã bắt đầu, không thể đăng ký thêm"
      );
    }

    if (!"OPEN".equals(session.getStatus())) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Buổi chơi đã đầy hoặc không còn mở đăng ký"
      );
    }
  }

  private User requireStaff(Long staffId) {
    if (staffId == null) {
      throw new BusinessException(
        HttpStatus.UNAUTHORIZED,

        "Không xác định được nhân viên đăng nhập"
      );
    }

    User staff = userRepository
      .findById(staffId)

      .orElseThrow(() ->
        new BusinessException(
          HttpStatus.NOT_FOUND,

          "Không tìm thấy nhân viên"
        )
      );

    if (!"STAFF".equals(staff.getRole()) && !"ADMIN".equals(staff.getRole())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Chỉ STAFF hoặc ADMIN được thực hiện thao tác này"
      );
    }

    return staff;
  }

  private DailyVisitorParticipant findParticipantForUpdate(Long participantId) {
    if (participantId == null || participantId <= 0) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "ID lượt đăng ký không hợp lệ"
      );
    }

    DailyVisitorParticipant participant = participantRepository
      .findById(participantId)

      .orElseThrow(() ->
        new BusinessException(
          HttpStatus.NOT_FOUND,

          "Không tìm thấy lượt đăng ký"
        )
      );

    DailyVisitorSession lockedSession = waitlistService.lockSession(
      participant.getSession().getId()
    );

    // Session có thể đã được tải khi đọc participant.

    // Đọc lại sau khi lấy khóa để tránh dùng dữ liệu cũ.

    entityManager.refresh(lockedSession);

    entityManager.refresh(participant);

    return participant;
  }

  private DailyVisitorParticipant cancelParticipant(
    DailyVisitorParticipant participant
  ) {
    validateParticipantCanBeCancelled(participant);

    DailyVisitorSession session = participant.getSession();

    validateSessionCanBeCancelled(session);

    validateCancellationDeadline(session);

    participant.setStatus("CANCELLED");

    DailyVisitorParticipant savedParticipant =
      participantRepository.saveAndFlush(participant);

    // Slot vừa trống được ưu tiên cho người đầu hàng.

    waitlistService.processSession(session.getId());

    return savedParticipant;
  }

  private void validateParticipantCanBeCancelled(
    DailyVisitorParticipant participant
  ) {
    if ("CONFIRMED".equals(participant.getStatus())) {
      return;
    }

    String message;

    if ("CANCELLED".equals(participant.getStatus())) {
      message = "Lượt đăng ký đã được hủy trước đó";
    } else if ("CHECKED_IN".equals(participant.getStatus())) {
      message = "Lượt đăng ký đã check-in nên không thể hủy";
    } else if ("NO_SHOW".equals(participant.getStatus())) {
      message = "Lượt đăng ký đã bị đánh dấu NO_SHOW";
    } else {
      message = "Trạng thái hiện tại không cho phép hủy";
    }

    throw new BusinessException(
      HttpStatus.CONFLICT,

      message
    );
  }

  private void validateSessionCanBeCancelled(DailyVisitorSession session) {
    if (session == null) {
      throw new BusinessException(
        HttpStatus.NOT_FOUND,

        "Không tìm thấy buổi chơi"
      );
    }

    if (
      "CANCELLED".equals(session.getStatus()) ||
      "CLOSED".equals(session.getStatus())
    ) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Buổi chơi đã bị hủy hoặc đã đóng"
      );
    }
  }

  private void validateCancellationDeadline(DailyVisitorSession session) {
    LocalDateTime cancelDeadline = getSessionStart(session).minusMinutes(30);

    if (!LocalDateTime.now().isBefore(cancelDeadline)) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Không thể hủy khi còn 30 phút hoặc ít hơn đến giờ chơi"
      );
    }
  }

  private LocalDateTime getSessionStart(DailyVisitorSession session) {
    return LocalDateTime.of(
      session.getSessionDate(),

      session.getStartTime()
    );
  }
}
