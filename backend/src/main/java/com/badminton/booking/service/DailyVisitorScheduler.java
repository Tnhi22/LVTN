package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.User;
import com.badminton.booking.entity.Visitor;

import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.repository.VisitorRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DailyVisitorScheduler {

    private final DailyVisitorSessionRepository sessionRepository;
    private final DailyVisitorParticipantRepository participantRepository;
    private final VisitorRepository visitorRepository;
    private final UserRepository userRepository;

    public DailyVisitorScheduler(
            DailyVisitorSessionRepository sessionRepository,
            DailyVisitorParticipantRepository participantRepository,
            VisitorRepository visitorRepository,
            UserRepository userRepository) {

        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.visitorRepository = visitorRepository;
        this.userRepository = userRepository;
    }


    // =========================================================
    // T + 30 PHÚT
    //
    // 1. Người vẫn CONFIRMED -> NO_SHOW
    // 2. CUSTOMER NO_SHOW -> WARNING / SUSPENDED
    // 3. Khách vãng lai NO_SHOW -> BLOCKED theo SĐT
    // 4. Đếm tổng slot CHECKED_IN
    // 5. Nếu CHECKED_IN < minParticipants -> CANCELLED
    // =========================================================
    @Scheduled(fixedRate = 60000)
    public void checkMinimumCheckedInAfterStart() {

        LocalDateTime now = LocalDateTime.now();

        List<DailyVisitorSession> sessions =
                sessionRepository.findAll();

        for (DailyVisitorSession session : sessions) {

            // Chỉ xử lý session còn hoạt động
            if (!"OPEN".equals(session.getStatus())
                    && !"FULL".equals(session.getStatus())) {

                continue;
            }

            LocalDateTime startTime =
                    LocalDateTime.of(
                            session.getSessionDate(),
                            session.getStartTime()
                    );

            LocalDateTime checkInDeadline =
                    startTime.plusMinutes(30);

            // Chưa tới T+30 thì chưa xử lý NO_SHOW
            if (now.isBefore(checkInDeadline)) {
                continue;
            }


            // =================================================
            // Lấy toàn bộ participant của session
            // =================================================
            List<DailyVisitorParticipant> participants =
                    participantRepository.findBySessionId(
                            session.getId()
                    );


            // =================================================
            // Tổng số slot đã đăng ký
            // =================================================
            long registeredSlots = 0L;

            for (DailyVisitorParticipant participant : participants) {

                Integer slotCount =
                        participant.getSlotCount();

                if (slotCount != null) {
                    registeredSlots += slotCount;
                }
            }


            // =================================================
            // Nếu ứng dụng bị tắt ở thời điểm T-30
            // và session vốn chưa đủ người đăng ký
            // =================================================
            if (registeredSlots
                    < session.getMinParticipants()) {

                session.setStatus("CANCELLED");

                session.setCancelReason(
                        "NOT_ENOUGH_REGISTERED_PLAYERS"
                );

                sessionRepository.save(session);

                System.out.println(
                        "Daily Visitor session "
                                + session.getId()
                                + " CANCELLED: "
                                + registeredSlots
                                + "/"
                                + session.getMinParticipants()
                                + " registered."
                );

                continue;
            }


            // =================================================
            // XỬ LÝ NO_SHOW
            // =================================================
            for (DailyVisitorParticipant participant : participants) {

                // Ai đã CHECKED_IN thì không xử lý
                if (!"CONFIRMED".equals(
                        participant.getStatus())) {

                    continue;
                }

                // Qua T+30 mà vẫn CONFIRMED
                // => NO_SHOW
                participant.setStatus("NO_SHOW");

                participantRepository.save(participant);


                // =============================================
                // CASE 1:
                // CUSTOMER có tài khoản
                // =============================================
                if (participant.getUser() != null) {

                    User user =
                            participant.getUser();

                    if ("ACTIVE".equals(user.getStatus())) {

                        user.setStatus("WARNING");

                    } else if ("WARNING".equals(
                            user.getStatus())) {

                        user.setStatus("SUSPENDED");
                    }

                    userRepository.save(user);

                    continue;
                }


                // =============================================
                // CASE 2:
                // Khách vãng lai
                // =============================================
                String phone =
                        participant.getRepresentativePhone();

                if (phone == null
                        || phone.isBlank()) {

                    continue;
                }

                String normalizedPhone =
                        phone.trim();


                Visitor visitor =
                        visitorRepository
                                .findByPhone(normalizedPhone)
                                .orElseGet(() -> {

                                    Visitor newVisitor =
                                            new Visitor();

                                    String participantName =
                                            participant
                                                    .getParticipantName();

                                    if (participantName == null
                                            || participantName
                                            .isBlank()) {

                                        participantName =
                                                "Khách vãng lai "
                                                        + normalizedPhone;
                                    }

                                    newVisitor.setFullName(
                                            participantName
                                    );

                                    newVisitor.setPhone(
                                            normalizedPhone
                                    );

                                    newVisitor.setStatus(
                                            "ACTIVE"
                                    );

                                    return visitorRepository
                                            .save(newVisitor);
                                });


                visitor.setStatus("BLOCKED");

                visitorRepository.save(visitor);
            }


            // =================================================
            // ĐẾM SLOT THỰC TẾ ĐÃ CHECK-IN
            //
            // Không dùng checked_in_slots.
            //
            // Ví dụ:
            // participant A:
            // slot_count = 1
            // CHECKED_IN
            // => tính 1
            //
            // participant B:
            // slot_count = 3
            // NO_SHOW
            // => tính 0
            // =================================================
            long checkedInSlots = 0L;

            for (DailyVisitorParticipant participant : participants) {

                if (!"CHECKED_IN".equals(
                        participant.getStatus())) {

                    continue;
                }

                Integer slotCount =
                        participant.getSlotCount();

                if (slotCount != null) {
                    checkedInSlots += slotCount;
                }
            }


            // =================================================
            // Không đủ người thực tế tới sân
            // =================================================
            if (checkedInSlots
                    < session.getMinParticipants()) {

                session.setStatus("CANCELLED");

                session.setCancelReason(
                        "NOT_ENOUGH_CHECKED_IN_PLAYERS"
                );

                sessionRepository.save(session);

                System.out.println(
                        "Daily Visitor session "
                                + session.getId()
                                + " CANCELLED: "
                                + checkedInSlots
                                + "/"
                                + session.getMinParticipants()
                                + " checked-in."
                );

                continue;
            }


            // =================================================
            // Nếu đủ người check-in
            // Không hủy session
            // =================================================
            System.out.println(
                    "Daily Visitor session "
                            + session.getId()
                            + " đủ người: "
                            + checkedInSlots
                            + "/"
                            + session.getMinParticipants()
                            + " checked-in."
            );
        }
    }
}