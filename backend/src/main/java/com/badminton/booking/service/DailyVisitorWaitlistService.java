package com.badminton.booking.service;

import com.badminton.booking.dto.DailyVisitorWaitlistResponse;
import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.DailyVisitorWaitlist;
import com.badminton.booking.entity.User;
import com.badminton.booking.entity.WaitlistStatus;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.repository.DailyVisitorWaitlistRepository;
import com.badminton.booking.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyVisitorWaitlistService {

  private final DailyVisitorWaitlistRepository waitlistRepository;

  private final DailyVisitorSessionRepository sessionRepository;

  private final DailyVisitorParticipantRepository participantRepository;

  private final UserRepository userRepository;

  private final DailyVisitorRegistrationGuard registrationGuard;

  @Value("${app.daily-visitor.waitlist.max-size:5}")
  private int maxWaitlistSize;

  public DailyVisitorWaitlistService(
    DailyVisitorWaitlistRepository waitlistRepository,
    DailyVisitorSessionRepository sessionRepository,
    DailyVisitorParticipantRepository participantRepository,
    UserRepository userRepository,
    DailyVisitorRegistrationGuard registrationGuard
  ) {
    this.waitlistRepository = waitlistRepository;

    this.sessionRepository = sessionRepository;

    this.participantRepository = participantRepository;

    this.userRepository = userRepository;
    this.registrationGuard = registrationGuard;
  }

  // =========================================================

  // Khóa session dùng chung cho đăng ký, hủy và waiting

  // =========================================================

  @Transactional
  public DailyVisitorSession lockSession(Long sessionId) {
    if (sessionId == null || sessionId <= 0) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Vui lòng chọn buổi chơi hợp lệ"
      );
    }

    return sessionRepository
      .findByIdForPayment(sessionId)

      .orElseThrow(() ->
        new BusinessException(
          HttpStatus.NOT_FOUND,

          "Không tìm thấy buổi chơi"
        )
      );
  }

  // =========================================================

  // Xử lý hết hạn và mời người theo thứ tự

  // =========================================================

  @Transactional
  public void processSession(Long sessionId) {
    DailyVisitorSession session = lockSession(sessionId);

    LocalDateTime now = LocalDateTime.now();

    LocalDateTime sessionStart = getSessionStart(session);

    List<DailyVisitorWaitlist> activeEntries =
      waitlistRepository.findBySession_IdAndStatusInOrderByCreatedAtAscIdAsc(
        sessionId,

        List.of(
          WaitlistStatus.WAITING,

          WaitlistStatus.OFFERED
        )
      );

    if ("CANCELLED".equals(session.getStatus()) || "CLOSED".equals(session.getStatus())) {
      closeEntries(
        activeEntries,

        WaitlistStatus.UNAVAILABLE,

        now
      );

      return;
    }

    // Đúng giờ bắt đầu: kết thúc hàng chờ và lời mời.

    if (!now.isBefore(sessionStart)) {
      closeEntries(
        activeEntries,

        WaitlistStatus.EXPIRED,

        now
      );

      return;
    }

    if (
      (!"OPEN".equals(session.getStatus()) && !"FULL".equals(session.getStatus())) ||
      !courtAvailable(session)
    ) {
      closeEntries(
        activeEntries,

        WaitlistStatus.UNAVAILABLE,

        now
      );

      return;
    }

    // Lời mời hết hạn không tiếp tục giữ slot.

    for (DailyVisitorWaitlist entry : activeEntries) {
      if (
        entry.getStatus() == WaitlistStatus.OFFERED &&
        (entry.getOfferExpiresAt() == null || !now.isBefore(entry.getOfferExpiresAt()))
      ) {
        entry.setStatus(WaitlistStatus.EXPIRED);

        entry.setResolvedAt(now);
      }
    }

    waitlistRepository.flush();

    long availableSlots = getAvailableSlots(session);

    if (availableSlots > 0) {
      List<DailyVisitorWaitlist> waitingEntries =
        waitlistRepository.findBySession_IdAndStatusOrderByCreatedAtAscIdAsc(
          sessionId,

          WaitlistStatus.WAITING
        );

      for (DailyVisitorWaitlist entry : waitingEntries) {
        if (availableSlots <= 0) {
          break;
        }

        LocalDateTime offerDeadline = now.plusMinutes(5);

        if (offerDeadline.isAfter(sessionStart)) {
          offerDeadline = sessionStart;
        }

        entry.setStatus(WaitlistStatus.OFFERED);

        entry.setOfferedAt(now);

        entry.setOfferExpiresAt(offerDeadline);

        entry.setResolvedAt(null);

        availableSlots--;
      }

      waitlistRepository.flush();
    }

    session.setStatus(getAvailableSlots(session) > 0 ? "OPEN" : "FULL");

    sessionRepository.save(session);
  }

  // Chỉ gọi sau khi khóa session và xử lý lời mời hết hạn.

  @Transactional
  public long getAvailableSlots(DailyVisitorSession session) {
    Long usedSlots = participantRepository.getUsedSlots(session.getId());

    long registeredSlots = usedSlots == null ? 0L : usedSlots;

    long heldSlots = waitlistRepository.countBySession_IdAndStatus(
      session.getId(),

      WaitlistStatus.OFFERED
    );

    return Math.max(
      0L,

      session.getMaxParticipants() - registeredSlots - heldSlots
    );
  }

  // =========================================================

  // Tham gia danh sách chờ

  // =========================================================

  @Transactional
  public DailyVisitorWaitlist joinWaitlist(Long sessionId, Long userId) {
    registrationGuard.lockAccount(userId);
    DailyVisitorSession session = lockSession(sessionId);

    User user = requireCustomer(userId);
    registrationGuard.requireNoOverlap(userId, session);

    LocalDateTime now = LocalDateTime.now();

    if (!now.isBefore(getSessionStart(session))) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Buổi chơi đã bắt đầu, danh sách chờ đã hết hạn"
      );
    }

    if (
      (!"OPEN".equals(session.getStatus()) && !"FULL".equals(session.getStatus())) ||
      !courtAvailable(session)
    ) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Buổi chơi không còn nhận danh sách chờ"
      );
    }

    if (hasActiveRegistration(sessionId, userId)) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Bạn đã đăng ký buổi chơi này"
      );
    }

    if (
      waitlistRepository

        .findBySession_IdAndUser_Id(sessionId, userId)

        .isPresent()
    ) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Bạn đã có yêu cầu chờ cho buổi chơi này"
      );
    }

    List<DailyVisitorWaitlist> activeEntries =
      waitlistRepository.findBySession_IdAndStatusInOrderByCreatedAtAscIdAsc(
        sessionId,

        List.of(
          WaitlistStatus.WAITING,

          WaitlistStatus.OFFERED
        )
      );

    long validOffers = activeEntries
      .stream()

      .filter(
        entry ->
          entry.getStatus() == WaitlistStatus.OFFERED &&
          entry.getOfferExpiresAt() != null &&
          now.isBefore(entry.getOfferExpiresAt())
      )

      .count();

    long waitingCount = activeEntries
      .stream()

      .filter(entry -> entry.getStatus() == WaitlistStatus.WAITING)

      .count();

    Long usedSlots = participantRepository.getUsedSlots(sessionId);

    long registeredSlots = usedSlots == null ? 0L : usedSlots;

    if (registeredSlots + validOffers < session.getMaxParticipants() && waitingCount == 0) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Buổi chơi còn slot, bạn có thể đăng ký trực tiếp"
      );
    }

    if (waitingCount + validOffers >= maxWaitlistSize) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Danh sách chờ đã đủ " + maxWaitlistSize + " người. Vui lòng chọn buổi khác."
      );
    }

    DailyVisitorWaitlist entry = new DailyVisitorWaitlist();

    entry.setSession(session);

    entry.setUser(user);

    entry.setStatus(WaitlistStatus.WAITING);

    DailyVisitorWaitlist saved = waitlistRepository.saveAndFlush(entry);

    processSession(sessionId);

    return saved;
  }

  // =========================================================

  // Xác nhận lời mời

  // =========================================================

  @Transactional
  public DailyVisitorWaitlist confirmOffer(Long sessionId, Long userId) {
    registrationGuard.lockAccount(userId);
    DailyVisitorSession session = lockSession(sessionId);

    if (!courtAvailable(session)) throw new BusinessException(
      HttpStatus.CONFLICT,
      "Sân vãng lai đang ngừng hoạt động"
    );

    DailyVisitorWaitlist entry = findMyWaitlist(sessionId, userId);

    processSession(sessionId);

    // Trả trạng thái thay vì rollback cập nhật hết hạn.

    if (
      entry.getStatus() == WaitlistStatus.EXPIRED ||
      entry.getStatus() == WaitlistStatus.UNAVAILABLE ||
      entry.getStatus() == WaitlistStatus.CONFIRMED
    ) {
      return entry;
    }

    if (entry.getStatus() != WaitlistStatus.OFFERED) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Bạn chưa có lời mời nhận slot"
      );
    }

    User user = requireCustomer(userId);

    if (hasActiveRegistration(sessionId, userId)) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Bạn đã có đăng ký cho buổi chơi này"
      );
    }

    LocalDateTime now = LocalDateTime.now();

    if (
      !now.isBefore(getSessionStart(session)) ||
      entry.getOfferExpiresAt() == null ||
      !now.isBefore(entry.getOfferExpiresAt())
    ) {
      entry.setStatus(WaitlistStatus.EXPIRED);

      entry.setResolvedAt(now);

      waitlistRepository.flush();

      processSession(sessionId);

      return entry;
    }

    Long usedSlots = participantRepository.getUsedSlots(sessionId);

    long registeredSlots = usedSlots == null ? 0L : usedSlots;

    if (registeredSlots >= session.getMaxParticipants()) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Buổi chơi đã đủ người, không thể xác nhận slot"
      );
    }

    registrationGuard.requireNoOverlap(userId, session);

    DailyVisitorParticipant participant = new DailyVisitorParticipant();

    participant.setSession(session);

    participant.setUser(user);

    participant.setParticipantName(user.getFullName().trim());

    participant.setSlotCount(1);

    participant.setCheckedInSlots(0);

    participant.setStatus("CONFIRMED");

    DailyVisitorParticipant savedParticipant = participantRepository.saveAndFlush(participant);

    entry.setParticipant(savedParticipant);

    entry.setStatus(WaitlistStatus.CONFIRMED);

    entry.setResolvedAt(now);

    waitlistRepository.flush();

    processSession(sessionId);

    return entry;
  }

  // =========================================================

  // Rời hàng chờ hoặc từ chối lời mời

  // =========================================================

  @Transactional
  public DailyVisitorWaitlist leaveWaitlist(Long sessionId, Long userId) {
    lockSession(sessionId);

    DailyVisitorWaitlist entry = findMyWaitlist(sessionId, userId);

    processSession(sessionId);

    if (
      entry.getStatus() == WaitlistStatus.EXPIRED ||
      entry.getStatus() == WaitlistStatus.UNAVAILABLE ||
      entry.getStatus() == WaitlistStatus.CANCELLED ||
      entry.getStatus() == WaitlistStatus.DECLINED
    ) {
      return entry;
    }

    if (entry.getStatus() == WaitlistStatus.CONFIRMED) {
      throw new BusinessException(
        HttpStatus.CONFLICT,

        "Bạn đã đăng ký thành công. " + "Vui lòng dùng chức năng hủy đăng ký."
      );
    }

    entry.setStatus(
      entry.getStatus() == WaitlistStatus.OFFERED
        ? WaitlistStatus.DECLINED
        : WaitlistStatus.CANCELLED
    );

    entry.setResolvedAt(LocalDateTime.now());

    waitlistRepository.flush();

    processSession(sessionId);

    return entry;
  }

  // =========================================================

  // Xem trạng thái của chính mình

  // =========================================================

  @Transactional
  public DailyVisitorWaitlistResponse getMyStatus(Long sessionId, Long userId) {
    lockSession(sessionId);

    DailyVisitorWaitlist entry = findMyWaitlist(sessionId, userId);

    processSession(sessionId);

    return toResponse(entry);
  }

  // Gọi trong transaction đang giữ khóa session.

  public DailyVisitorWaitlistResponse toResponse(DailyVisitorWaitlist entry) {
    Integer position = null;

    if (entry.getStatus() == WaitlistStatus.WAITING) {
      List<DailyVisitorWaitlist> queue =
        waitlistRepository.findBySession_IdAndStatusOrderByCreatedAtAscIdAsc(
          entry.getSession().getId(),

          WaitlistStatus.WAITING
        );

      for (int index = 0; index < queue.size(); index++) {
        if (queue.get(index).getId().equals(entry.getId())) {
          position = index + 1;

          break;
        }
      }
    }

    String message = switch (entry.getStatus()) {
      case WAITING -> "Bạn đang ở vị trí " + position + " trong danh sách chờ.";
      case OFFERED -> "Buổi chơi đã có slot trống. " + "Vui lòng xác nhận trước thời hạn.";
      case CONFIRMED -> "Bạn đã xác nhận đăng ký thành công.";
      case DECLINED -> "Bạn đã từ chối lời mời nhận slot.";
      case CANCELLED -> "Bạn đã rời danh sách chờ.";
      case EXPIRED -> "Yêu cầu chờ hoặc lời mời đã hết hạn. " + "Bạn có thể chọn buổi chơi khác.";
      case UNAVAILABLE -> "Buổi chơi không còn nhận người tham gia. " +
        "Bạn có thể chọn buổi khác.";
    };

    DailyVisitorSession session = entry.getSession();

    return new DailyVisitorWaitlistResponse(
      entry.getId(),

      session.getId(),

      session.getSessionDate(),

      session.getStartTime(),

      session.getEndTime(),

      entry.getStatus(),

      position,

      entry.getCreatedAt(),

      entry.getOfferedAt(),

      entry.getOfferExpiresAt(),

      entry.getParticipant() == null ? null : entry.getParticipant().getId(),

      message
    );
  }

  // =========================================================

  // Các hàm hỗ trợ

  // =========================================================

  private DailyVisitorWaitlist findMyWaitlist(Long sessionId, Long userId) {
    if (userId == null) {
      throw new BusinessException(
        HttpStatus.UNAUTHORIZED,

        "Không xác định được tài khoản đăng nhập"
      );
    }

    return waitlistRepository

      .findBySession_IdAndUser_Id(sessionId, userId)

      .orElseThrow(() ->
        new BusinessException(
          HttpStatus.NOT_FOUND,

          "Bạn chưa tham gia danh sách chờ của buổi này"
        )
      );
  }

  private User requireCustomer(Long userId) {
    if (userId == null) {
      throw new BusinessException(
        HttpStatus.UNAUTHORIZED,

        "Không xác định được tài khoản đăng nhập"
      );
    }

    User user = userRepository
      .findById(userId)

      .orElseThrow(() ->
        new BusinessException(
          HttpStatus.NOT_FOUND,

          "Không tìm thấy người dùng"
        )
      );

    if (!"CUSTOMER".equals(user.getRole())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Chỉ khách hàng được tham gia danh sách chờ"
      );
    }

    if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Vui lòng xác minh số điện thoại trước khi tham gia"
      );
    }

    if ("SUSPENDED".equals(user.getStatus())) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,

        "Tài khoản đã bị khóa"
      );
    }

    if (user.getFullName() == null || user.getFullName().isBlank()) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,

        "Vui lòng cập nhật họ tên trước khi tham gia"
      );
    }

    return user;
  }

  private boolean hasActiveRegistration(Long sessionId, Long userId) {
    return participantRepository
      .findBySessionId(sessionId)

      .stream()

      .anyMatch(
        participant ->
          participant.getUser() != null &&
          userId.equals(participant.getUser().getId()) &&
          !"CANCELLED".equals(participant.getStatus())
      );
  }

  private LocalDateTime getSessionStart(DailyVisitorSession session) {
    return LocalDateTime.of(
      session.getSessionDate(),

      session.getStartTime()
    );
  }

  private void closeEntries(
    List<DailyVisitorWaitlist> entries,
    WaitlistStatus status,
    LocalDateTime now
  ) {
    for (DailyVisitorWaitlist entry : entries) {
      entry.setStatus(status);

      entry.setResolvedAt(now);
    }

    waitlistRepository.flush();
  }

  private boolean courtAvailable(DailyVisitorSession session) {
    var schedule = session.getSchedule();

    var court = schedule.getCourt();

    return (
      Boolean.TRUE.equals(schedule.getActive()) &&
      Boolean.TRUE.equals(court.getActive()) &&
      Boolean.TRUE.equals(court.getRoom().getActive()) &&
      Boolean.TRUE.equals(court.getRoom().getCourtType().getActive())
    );
  }
}
