package com.badminton.booking.service;

import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.User;
import com.badminton.booking.entity.UserViolation;
import com.badminton.booking.entity.Visitor;

import com.badminton.booking.repository.NormalBookingRepository;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.repository.UserViolationRepository;
import com.badminton.booking.repository.VisitorRepository;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingScheduler {

    private final NormalBookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final UserViolationRepository violationRepository;
    private final VisitorRepository visitorRepository;
    private final DailyVisitorSessionRepository dailyVisitorSessionRepository;
    private final DailyVisitorParticipantRepository dailyVisitorParticipantRepository;

    public BookingScheduler(
            NormalBookingRepository bookingRepository,
            UserRepository userRepository,
            UserViolationRepository violationRepository,
            VisitorRepository visitorRepository,
            DailyVisitorParticipantRepository dailyVisitorParticipantRepository,
            DailyVisitorSessionRepository dailyVisitorSessionRepository) {

        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.violationRepository = violationRepository;
        this.visitorRepository = visitorRepository;

        this.dailyVisitorParticipantRepository =
                dailyVisitorParticipantRepository;

        this.dailyVisitorSessionRepository =
                dailyVisitorSessionRepository;
    }

    @Scheduled(fixedRate = 60000)
    public void updateNoShowBookings() {

        List<NormalBooking> bookings =
                bookingRepository.findAll();

        LocalDateTime now = LocalDateTime.now();

        for (NormalBooking booking : bookings) {

            String status = booking.getStatus();

            if (!"PENDING".equals(status)
                    && !"NO_SHOW_PENDING".equals(status)) {
                continue;
            }

            LocalDateTime bookingStart =
                    LocalDateTime.of(
                            booking.getBookingDate(),
                            booking.getStartTime()
                    );

            LocalDateTime warningTime =
                    bookingStart.plusMinutes(15);

            LocalDateTime finalTime =
                    bookingStart.plusMinutes(30);

            // Sau 15 phút:
            // no-show tạm thời, chưa phạt, chưa nhả sân
            if ("PENDING".equals(status)
                    && !now.isBefore(warningTime)) {

                booking.setStatus("NO_SHOW_PENDING");
                bookingRepository.save(booking);

                status = "NO_SHOW_PENDING";
            }

            // Chưa đủ 30 phút thì vẫn chờ
            if (!"NO_SHOW_PENDING".equals(status)
                    || now.isBefore(finalTime)) {
                continue;
            }

            // ==========================================
            // QUÁ 30 PHÚT -> NO_SHOW -> NHẢ SÂN
            // ==========================================
            booking.setStatus("NO_SHOW");
            bookingRepository.save(booking);

            // ==========================================
            // 1. VISITOR - KHÁCH VÃNG LAI
            // ==========================================
            if (booking.getVisitor() != null) {

                Visitor visitor = booking.getVisitor();

                visitor.setStatus("BLOCKED");
                visitorRepository.save(visitor);

                // Không xử lý UserViolation
                continue;
            }

            // ==========================================
            // 2. CUSTOMER CÓ TÀI KHOẢN
            // ==========================================
            User user = booking.getUser();

            if (user == null) {
                continue;
            }

            String newStatus;

            if ("ACTIVE".equals(user.getStatus())) {
                newStatus = "WARNING";

            } else if ("WARNING".equals(user.getStatus())) {
                newStatus = "SUSPENDED";

            } else {
                newStatus = user.getStatus();
            }

            user.setStatus(newStatus);
            userRepository.save(user);

            UserViolation violation =
                    new UserViolation();

            violation.setUser(user);
            violation.setBooking(booking);
            violation.setViolationType("NO_SHOW");
            violation.setUserStatusAfter(newStatus);

            violationRepository.save(violation);
        }
    }


    @Scheduled(fixedRate = 60000)
    public void checkDailyVisitorMinimumParticipants() {

        List<DailyVisitorSession> sessions =
                dailyVisitorSessionRepository.findAll();

        LocalDateTime now = LocalDateTime.now();

        for (DailyVisitorSession session : sessions) {

            // Chỉ xét session còn OPEN
            if (!"OPEN".equals(session.getStatus())) {
                continue;
            }

            LocalDateTime sessionStart =
                    LocalDateTime.of(
                            session.getSessionDate(),
                            session.getStartTime()
                    );

            // Mốc chốt: trước giờ chơi 30 phút
            LocalDateTime closeRegistrationTime =
                    sessionStart.minusMinutes(30);

            // Chưa tới T-30 thì chưa xử lý
            if (now.isBefore(closeRegistrationTime)) {
                continue;
            }

            /*
            * Nếu đã qua giờ bắt đầu thì T-30 cũng đã qua.
            * Với rule hiện tại, session OPEN mà chưa được xử lý
            * vẫn phải kiểm tra số người đăng ký.
            */

            Long usedSlots =
                    dailyVisitorParticipantRepository
                            .getUsedSlots(session.getId());

            if (usedSlots == null) {
                usedSlots = 0L;
            }

            // Không đủ số người tối thiểu
            if (usedSlots < session.getMinParticipants()) {

                session.setStatus("CANCELLED");
                session.setCancelReason(
                        "NOT_ENOUGH_REGISTERED_PLAYERS"
                );

                dailyVisitorSessionRepository.save(session);

                System.out.println(
                        "Daily Visitor session "
                                + session.getId()
                                + " bi huy: "
                                + usedSlots
                                + "/"
                                + session.getMinParticipants()
                                + " nguoi dang ky."
                );
            }
        }
    }
}