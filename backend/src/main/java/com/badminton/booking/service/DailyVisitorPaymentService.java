package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyVisitorPaymentService {

  private final DailyVisitorSessionRepository sessionRepository;
  private final DailyVisitorParticipantRepository participantRepository;
  private final DailyVisitorFeePolicy feePolicy;

  public DailyVisitorPaymentService(
    DailyVisitorSessionRepository sessionRepository,
    DailyVisitorParticipantRepository participantRepository,
    DailyVisitorFeePolicy feePolicy
  ) {
    this.sessionRepository = sessionRepository;
    this.participantRepository = participantRepository;
    this.feePolicy = feePolicy;
  }

  /*
   * Gọi trong cùng transaction của check-in.
   * Khóa session để hai nhân viên không cùng chốt giá một lúc.
   */
  @Transactional
  public DailyVisitorSession lockSession(Long sessionId) {
    return sessionRepository
      .findByIdForPayment(sessionId)
      .orElseThrow(() ->
        new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy lượt Daily Visitor")
      );
  }

  @Transactional
  public void collectAtCheckIn(
    DailyVisitorParticipant participant,
    DailyVisitorSession session,
    Long staffId
  ) {
    // Recalculate unpaid registrations using the shared per-person configuration.
    // Ignore legacy session totals and headcount-based allocations.
    allocateFees(session);

    if (participant.getAmountDue() == null) {
      throw new BusinessException(HttpStatus.CONFLICT, "Lượt đăng ký chưa được phân bổ tiền");
    }

    if (participant.getPaidAt() != null) {
      throw new BusinessException(HttpStatus.CONFLICT, "Lượt đăng ký này đã thu tiền");
    }

    // Theo quy tắc test: check-in đồng nghĩa đã thu đủ tiền mặt tại quầy.
    participant.setAmountPaid(participant.getAmountDue());
    participant.setPaidAt(LocalDateTime.now());
    participant.setPaidBy(staffId);
    participantRepository.save(participant);
  }

  private void allocateFees(DailyVisitorSession session) {
    List<DailyVisitorParticipant> participants = participantRepository
      .findBySessionId(session.getId())
      .stream()
      .filter(p -> "CONFIRMED".equals(p.getStatus()) || "CHECKED_IN".equals(p.getStatus()))
      .sorted(Comparator.comparing(DailyVisitorParticipant::getId))
      .toList();

    long slots = participants
      .stream()
      .mapToLong(p -> p.getSlotCount() == null ? 0 : p.getSlotCount())
      .sum();

    if (slots < session.getMinParticipants()) {
      throw new BusinessException(
        HttpStatus.CONFLICT,
        "Chưa đủ người tối thiểu để chốt phí Daily Visitor"
      );
    }

    long amountPerPerson = feePolicy.feePerPerson(session);

    for (DailyVisitorParticipant participant : participants) {
      // Do not rewrite money that was already collected.
      if (participant.getPaidAt() == null) {
        if (participant.getSlotCount() == null || participant.getSlotCount() <= 0) {
          throw new BusinessException(HttpStatus.CONFLICT, "Số người đăng ký không hợp lệ");
        }
        participant.setAmountDue(
          Math.multiplyExact(amountPerPerson, participant.getSlotCount().longValue())
        );
      }
    }
    participantRepository.saveAll(participants);

    long total = participants
      .stream()
      .mapToLong(p -> p.getAmountDue() == null ? 0L : p.getAmountDue())
      .sum();
    session.setFeeBaseAmount(Math.multiplyExact(amountPerPerson, slots));
    session.setFeePerSlot(amountPerPerson);
    session.setFeeTotalAmount(total);
    session.setFeeSlotCount((int) slots);
    if (session.getFeeLockedAt() == null) session.setFeeLockedAt(LocalDateTime.now());
    sessionRepository.save(session);
  }
}
