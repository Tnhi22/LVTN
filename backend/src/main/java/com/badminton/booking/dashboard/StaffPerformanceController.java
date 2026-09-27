package com.badminton.booking.dashboard;

import com.badminton.booking.entity.User;
import com.badminton.booking.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/admin/staff-performance")
public class StaffPerformanceController {

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public StaffPerformanceController(
            UserRepository userRepository,
            EntityManager entityManager) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    public record StaffPerformance(
            Long staffId,
            String fullName,
            String status,
            Long completedBookings,
            Long paidDailyVisitorRegistrations,
            Long cashCollected,
            Long bankTransferCollected,
            Long totalCollected
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<StaffPerformance> getStaffPerformance(
            @RequestParam(required = false) LocalDate date) {

        LocalDate targetDate = date != null
                ? date
                : LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.plusDays(1).atStartOfDay();

        return userRepository.findAll().stream()
                .filter(user -> "STAFF".equals(user.getRole()))
                .map(user -> buildPerformance(user, start, end))
                .toList();
    }

    private StaffPerformance buildPerformance(
            User staff,
            LocalDateTime start,
            LocalDateTime end) {

        Long staffId = staff.getId();

        Long completedBookings = entityManager.createQuery("""
                SELECT COUNT(b)
                FROM NormalBooking b
                WHERE b.status = 'COMPLETED'
                  AND b.completedBy = :staffId
                  AND b.completedAt >= :start
                  AND b.completedAt < :end
                """, Long.class)
                .setParameter("staffId", staffId)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();

        Long paidDailyRegistrations = entityManager.createQuery("""
                SELECT COUNT(p)
                FROM DailyVisitorParticipant p
                WHERE p.paidBy = :staffId
                  AND p.paidAt >= :start
                  AND p.paidAt < :end
                """, Long.class)
                .setParameter("staffId", staffId)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();

        Long normalCash = sumNormal(staffId, "CASH", start, end);
        Long transfer = sumNormal(
                staffId, "BANK_TRANSFER", start, end
        );

        Long dailyCash = entityManager.createQuery("""
                SELECT COALESCE(SUM(p.amountPaid), 0)
                FROM DailyVisitorParticipant p
                WHERE p.paidBy = :staffId
                  AND p.paidAt >= :start
                  AND p.paidAt < :end
                """, Long.class)
                .setParameter("staffId", staffId)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();

        long cash = normalCash + dailyCash;

        return new StaffPerformance(
                staffId,
                staff.getFullName(),
                staff.getStatus(),
                completedBookings,
                paidDailyRegistrations,
                cash,
                transfer,
                cash + transfer
        );
    }

    private Long sumNormal(
            Long staffId,
            String method,
            LocalDateTime start,
            LocalDateTime end) {

        return entityManager.createQuery("""
                SELECT COALESCE(SUM(b.totalAmount), 0)
                FROM NormalBooking b
                WHERE b.status = 'COMPLETED'
                  AND b.completedBy = :staffId
                  AND b.paymentMethod = :method
                  AND b.completedAt >= :start
                  AND b.completedAt < :end
                """, Long.class)
                .setParameter("staffId", staffId)
                .setParameter("method", method)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();
    }
}