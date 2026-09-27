package com.badminton.booking.service;

import com.badminton.booking.entity.CourtPrice;
import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.CourtPriceRepository;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
public class DailyVisitorPaymentService {

    private final DailyVisitorSessionRepository sessionRepository;
    private final DailyVisitorParticipantRepository participantRepository;
    private final CourtPriceRepository courtPriceRepository;

    public DailyVisitorPaymentService(
            DailyVisitorSessionRepository sessionRepository,
            DailyVisitorParticipantRepository participantRepository,
            CourtPriceRepository courtPriceRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.courtPriceRepository = courtPriceRepository;
    }

    /*
     * Gọi trong cùng transaction của check-in.
     * Khóa session để hai nhân viên không cùng chốt giá một lúc.
     */
    @Transactional
    public DailyVisitorSession lockSession(Long sessionId) {
        return sessionRepository.findByIdForPayment(sessionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lượt Daily Visitor"
                ));
    }

    @Transactional
    public void collectAtCheckIn(
            DailyVisitorParticipant participant,
            DailyVisitorSession session,
            Long staffId
    ) {
        if (session.getFeeLockedAt() == null) {
            allocateFees(session);
        }

        if (participant.getAmountDue() == null) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt đăng ký chưa được phân bổ tiền"
            );
        }

        if (participant.getPaidAt() != null) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt đăng ký này đã thu tiền"
            );
        }

        // Theo quy tắc test: check-in đồng nghĩa đã thu đủ tiền mặt tại quầy.
        participant.setAmountPaid(participant.getAmountDue());
        participant.setPaidAt(LocalDateTime.now());
        participant.setPaidBy(staffId);
        participantRepository.save(participant);
    }


    private void allocateFees(DailyVisitorSession session) {
        List<DailyVisitorParticipant> participants =
                participantRepository.findBySessionId(session.getId())
                        .stream()
                        .filter(p -> "CONFIRMED".equals(p.getStatus())
                                || "CHECKED_IN".equals(p.getStatus()))
                        .sorted(Comparator.comparing(
                                DailyVisitorParticipant::getId
                        ))
                        .toList();

        long slots = participants.stream()
                .mapToLong(p -> p.getSlotCount() == null
                        ? 0
                        : p.getSlotCount())
                .sum();

        if (slots < session.getMinParticipants()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Chưa đủ người tối thiểu để chốt phí Daily Visitor"
            );
        }

        long minutes = Duration.between(
                session.getStartTime(),
                session.getEndTime()
        ).toMinutes();

        if (minutes <= 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Thời gian buổi Daily Visitor không hợp lệ"
            );
        }

        // Giá sân cố định 80.000đ/giờ.
        long courtAmount = 80_000L * minutes / 60;

        if (session.getShuttlecockProduct() == null
                || session.getShuttlecockProduct().getTubePrice() == null
                || session.getShuttlecockQuantityTubes() == null) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Buổi chơi chưa có giá hoặc số lượng cầu hợp lệ"
            );
        }

        long shuttleAmount =
                session.getShuttlecockProduct().getTubePrice()
                        * session.getShuttlecockQuantityTubes();

        Long fixedFee = session.getFixedFeeSnapshot();

        if (fixedFee == null || fixedFee <= 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Buổi Daily Visitor chưa được chốt giá"
            );
        }

        long total = fixedFee;

        // Làm tròn giá mỗi slot lên đến 1.000đ.
        long amountPerSlot = (total + slots - 1) / slots;
        long roundedPerSlot =
                ((amountPerSlot + 999) / 1_000) * 1_000;

        for (DailyVisitorParticipant participant : participants) {
            participant.setAmountDue(
                    roundedPerSlot * participant.getSlotCount()
            );
        }

        participantRepository.saveAll(participants);

        session.setFeeBaseAmount(total);
        session.setFeePerSlot(roundedPerSlot);
        session.setFeeTotalAmount(roundedPerSlot * slots);
        session.setFeeSlotCount((int) slots);
        session.setFeeLockedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    private long calculateCourtAmount(
            CourtPrice price,
            LocalTime start,
            LocalTime end
    ) {
        if (start.isBefore(price.getOpeningTime())
                || end.isAfter(price.getClosingTime())
                || !end.isAfter(start)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Khung giờ Daily Visitor không hợp lệ với bảng giá sân"
            );
        }

        LocalTime peakStart = price.getPeakStartTime();

        long normalMinutes = 0;
        long peakMinutes = 0;

        if (start.isBefore(peakStart)) {
            LocalTime normalEnd = end.isBefore(peakStart)
                    ? end
                    : peakStart;

            normalMinutes =
                    Duration.between(start, normalEnd).toMinutes();
        }

        if (end.isAfter(peakStart)) {
            LocalTime actualPeakStart = start.isAfter(peakStart)
                    ? start
                    : peakStart;

            peakMinutes =
                    Duration.between(actualPeakStart, end).toMinutes();
        }

        return price.getNormalPricePerHour() * normalMinutes / 60
                + price.getPeakPricePerHour() * peakMinutes / 60;
    }
}