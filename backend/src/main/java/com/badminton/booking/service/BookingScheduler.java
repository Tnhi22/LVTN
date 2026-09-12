package com.badminton.booking.service;

import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.User;
import com.badminton.booking.entity.UserViolation;
import com.badminton.booking.repository.NormalBookingRepository;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.repository.UserViolationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingScheduler {

    private final NormalBookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final UserViolationRepository violationRepository;

    public BookingScheduler(
            NormalBookingRepository bookingRepository,
            UserRepository userRepository,
            UserViolationRepository violationRepository) {

        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.violationRepository = violationRepository;
    }

    @Scheduled(fixedRate = 60000)
    public void updateNoShowBookings() {
    List<NormalBooking> bookings = bookingRepository.findAll();
    LocalDateTime now = LocalDateTime.now();

    for (NormalBooking booking : bookings) {
        String status = booking.getStatus();

        if (!"PENDING".equals(status)
                && !"NO_SHOW_PENDING".equals(status)) {
            continue;
        }

        LocalDateTime bookingStart = LocalDateTime.of(
                booking.getBookingDate(),
                booking.getStartTime()
        );

        LocalDateTime warningTime = bookingStart.plusMinutes(15);
        LocalDateTime finalTime = bookingStart.plusMinutes(30);

        // Sau 15 phút: hiển thị no-show tạm thời, chưa phạt và chưa trả sân.
        if ("PENDING".equals(status) && !now.isBefore(warningTime)) {
            booking.setStatus("NO_SHOW_PENDING");
            bookingRepository.save(booking);
            status = "NO_SHOW_PENDING";
        }

        // Chưa đủ 30 phút thì khách vẫn còn thời gian khiếu nại.
        if (!"NO_SHOW_PENDING".equals(status)
                || now.isBefore(finalTime)) {
            continue;
        }

        // Khách đã khiếu nại, chờ nhân viên xử lý: chưa chốt no-show.
        if ("PENDING".equals(booking.getComplaintStatus())) {
            continue;
        }

        // Sau 30 phút, nếu không có khiếu nại đang chờ: chốt no-show.
        booking.setStatus("NO_SHOW");
        bookingRepository.save(booking);

        User user = booking.getUser();
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

        UserViolation violation = new UserViolation();
        violation.setUser(user);
        violation.setBooking(booking);
        violation.setViolationType("NO_SHOW");
        violation.setUserStatusAfter(newStatus);
        violationRepository.save(violation);
    }
    }
}