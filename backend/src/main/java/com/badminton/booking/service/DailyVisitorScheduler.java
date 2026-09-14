package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.entity.Visitor;
import com.badminton.booking.repository.VisitorRepository;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DailyVisitorScheduler {

    private final DailyVisitorSessionRepository sessionRepository;
    private final DailyVisitorParticipantRepository participantRepository;
    private final VisitorRepository visitorRepository;

    public DailyVisitorScheduler(
            DailyVisitorSessionRepository sessionRepository,
            DailyVisitorParticipantRepository participantRepository,
            VisitorRepository visitorRepository) {

        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.visitorRepository = visitorRepository;
    }

    @Scheduled(fixedRate = 60000)
public void checkMinimumCheckedInAfterStart() {

    LocalDateTime now = LocalDateTime.now();
    List<DailyVisitorSession> sessions =
            sessionRepository.findAll();

    for (DailyVisitorSession session : sessions) {

        // Session đã bị hủy ở T-30 thì không xử lý lại.
    if (!"OPEN".equals(session.getStatus())
            && !"FULL".equals(session.getStatus())) {
        continue;
    }

        LocalDateTime startTime = LocalDateTime.of(
                session.getSessionDate(),
                session.getStartTime()
        );

        LocalDateTime checkInDeadline =
                startTime.plusMinutes(30);

        // Chưa đến T+30.
        if (now.isBefore(checkInDeadline)) {
            continue;
        }

        Long registeredSlots =
                participantRepository.getUsedSlots(
                        session.getId()
                );

        Long checkedInSlots =
                participantRepository.getCheckedInSlots(
                        session.getId()
                );

        if (registeredSlots == null) {
            registeredSlots = 0L;
        }

        if (checkedInSlots == null) {
            checkedInSlots = 0L;
        }

        // Trường hợp ứng dụng không chạy ở mốc T-30:
        // vẫn ưu tiên xác định thiếu người đăng ký.
        if (registeredSlots < session.getMinParticipants()) {

            session.setStatus("CANCELLED");
            session.setCancelReason(
                    "NOT_ENOUGH_REGISTERED_PLAYERS"
            );

            sessionRepository.save(session);

            System.out.println(
                    "Daily Visitor session "
                            + session.getId()
                            + " bị hủy vì chỉ có "
                            + registeredSlots + "/"
                            + session.getMinParticipants()
                            + " người đăng ký."
            );

            continue;
        }

        // Đã đủ người đăng ký nhưng không đủ người thực tế check-in.
        if (checkedInSlots < session.getMinParticipants()) {

        session.setStatus("CANCELLED");
        List<DailyVisitorParticipant> participants =
        participantRepository.findBySessionId(session.getId());

        for (DailyVisitorParticipant participant : participants) {

            Integer registered = participant.getSlotCount();
            Integer checkedIn = participant.getCheckedInSlots();

            if (registered == null) {
                registered = 0;
            }

            if (checkedIn == null) {
                checkedIn = 0;
            }

            // Chỉ chặn nhóm hoàn toàn không đến.
            if (registered > 0 && checkedIn == 0) {

                participant.setStatus("NO_SHOW");
                participantRepository.save(participant);

                String phone = participant.getRepresentativePhone();

                if (phone != null && !phone.isBlank()) {
                    String normalizedPhone = phone.trim();

                    Visitor visitor = visitorRepository
                            .findByPhone(normalizedPhone)
                            .orElseGet(() -> {
                                Visitor newVisitor = new Visitor();
                                newVisitor.setFullName(
                                        participant.getParticipantName()
                                );
                                newVisitor.setPhone(normalizedPhone);
                                newVisitor.setStatus("ACTIVE");
                                return visitorRepository.save(newVisitor);
                            });

                    visitor.setStatus("BLOCKED");
                    visitorRepository.save(visitor);
                }
                }
            }
            session.setCancelReason(
                    "NOT_ENOUGH_CHECKED_IN_PLAYERS"
            );

            sessionRepository.save(session);

            System.out.println(
                    "Daily Visitor session "
                            + session.getId()
                            + " bị hủy vì chỉ có "
                            + checkedInSlots + "/"
                            + session.getMinParticipants()
                            + " người check-in."
            );
        }
    }
}
}