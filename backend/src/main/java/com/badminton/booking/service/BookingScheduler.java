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
    private final BookingInventoryService bookingInventoryService;

    public BookingScheduler(
            NormalBookingRepository bookingRepository,
            UserRepository userRepository,
            UserViolationRepository violationRepository,
            VisitorRepository visitorRepository,
            DailyVisitorParticipantRepository dailyVisitorParticipantRepository,
            DailyVisitorSessionRepository dailyVisitorSessionRepository,
            BookingInventoryService bookingInventoryService) {

        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.violationRepository = violationRepository;
        this.visitorRepository = visitorRepository;
        this.bookingInventoryService = bookingInventoryService;

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
                booking.setStatus("NO_SHOW");

                /*
                * Khách không đến nên không xuất kho.
                * Chỉ giải phóng số ống đã giữ để khách khác có thể mua.
                * Tồn kho vật lý không thay đổi.
                */
                bookingInventoryService.releaseReservation(booking);

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

                String oldStatus = user.getStatus();
                String newStatus;

                if ("ACTIVE".equals(oldStatus)) {
                newStatus = "WARNING";

                } else if ("WARNING".equals(oldStatus)) {
                newStatus = "SUSPENDED";

                } else {
                newStatus = oldStatus;
                }

                user.setStatus(newStatus);
                userRepository.save(user);

                // Vi phạm lần đầu: ACTIVE -> WARNING
                // Tự động hủy tất cả booking PENDING trong tương lai
                if ("ACTIVE".equals(oldStatus)
                        && "WARNING".equals(newStatus)) {

                cancelFuturePendingBookings(
                        user.getId(),
                        booking.getId(),
                        now
                );
                }

            UserViolation violation =
                    new UserViolation();

            violation.setUser(user);
            violation.setBooking(booking);
            violation.setViolationType("NO_SHOW");
            violation.setUserStatusAfter(newStatus);

        UserViolation savedViolation =
                violationRepository.save(violation);

        LocalDateTime restrictionEnd = null;

        if ("WARNING".equals(newStatus)) {
            restrictionEnd =
                    savedViolation.getCreatedAt().plusDays(2);
        }

        List<NormalBooking> userBookings =
                bookingRepository.findAll();

        for (NormalBooking otherBooking : userBookings) {

            // Không xử lý chính booking vừa NO_SHOW
            if (otherBooking.getId().equals(booking.getId())) {
                continue;
            }

            // Chỉ booking của CUSTOMER này
            if (otherBooking.getUser() == null
                    || !otherBooking.getUser().getId().equals(user.getId())) {
                continue;
            }

            // Chỉ tự hủy booking còn PENDING
            if (!"PENDING".equals(otherBooking.getStatus())) {
                continue;
            }

            LocalDateTime otherBookingStart =
                    LocalDateTime.of(
                            otherBooking.getBookingDate(),
                            otherBooking.getStartTime()
                    );

            // Booking đã qua thì không xử lý ở đây
            if (!otherBookingStart.isAfter(now)) {
                continue;
            }

            // Nếu WARNING:
            // chỉ hủy booking nằm trong 2 ngày bị hạn chế
            if ("WARNING".equals(newStatus)
                    && !otherBookingStart.isBefore(restrictionEnd)) {
                continue;
            }

            // WARNING trong thời hạn hoặc SUSPENDED
            otherBooking.setStatus("CANCELLED");
            bookingRepository.save(otherBooking);

            System.out.println(
                    "Auto-cancel booking "
                            + otherBooking.getId()
                            + " cua user "
                            + user.getId()
                            + " do user "
                            + newStatus
            );
        }
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


            // ==========================================
        // CHECKED_IN -> COMPLETED KHI HẾT GIỜ CHƠI
        // ==========================================
        @Scheduled(fixedRate = 60000)
        public void completeFinishedBookings() {

            List<NormalBooking> bookings =
                    bookingRepository.findAll();

            LocalDateTime now = LocalDateTime.now();

            for (NormalBooking booking : bookings) {

                // Chỉ xử lý booking đã check-in
                if (!"CHECKED_IN".equals(booking.getStatus())) {
                    continue;
                }

                LocalDateTime bookingEnd =
                        LocalDateTime.of(
                                booking.getBookingDate(),
                                booking.getEndTime()
                        );

                // Chưa hết giờ chơi thì giữ CHECKED_IN
                if (now.isBefore(bookingEnd)) {
                    continue;
                }

                // Đã hết giờ chơi
                booking.setStatus("COMPLETED");
                bookingRepository.save(booking);

                System.out.println(
                        "Booking "
                                + booking.getId()
                                + " da COMPLETED."
                );
            }
        }


                private void cancelFuturePendingBookings(
                Long userId,
                Long noShowBookingId,
                LocalDateTime now) {

        List<NormalBooking> pendingBookings =
                bookingRepository.findByUser_IdAndStatus(
                        userId,
                        "PENDING"
                );

        for (NormalBooking pendingBooking : pendingBookings) {

                // Không xử lý booking vừa chuyển thành NO_SHOW
                if (pendingBooking.getId().equals(noShowBookingId)) {
                continue;
                }

                LocalDateTime pendingStart =
                        LocalDateTime.of(
                                pendingBooking.getBookingDate(),
                                pendingBooking.getStartTime()
                        );

                // Chỉ hủy booking chưa tới giờ chơi
                if (pendingStart.isAfter(now)) {
                pendingBooking.setStatus("CANCELLED");
                pendingBooking.setCancelledAt(now);
                }
        }

        bookingRepository.saveAll(pendingBookings);
        }
}