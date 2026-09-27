package com.badminton.booking.dashboard;

import jakarta.persistence.EntityManager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/dashboard/staff-cash")
public class StaffCashDashboardController {

    private static final ZoneId VIETNAM_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final EntityManager entityManager;

    public StaffCashDashboardController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public record StaffCashSummary(
            LocalDate date,
            Long cashExpected,
            Long bankTransferRevenue,
            Long totalRevenue,
            Long myCashCollected,
            Long myBankTransferCollected
    ) {}

    @GetMapping("/today")
    @Transactional(readOnly = true)
    public StaffCashSummary getToday(@AuthenticationPrincipal Jwt jwt) {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        Long staffId = Long.valueOf(jwt.getSubject());

        // Tiền mặt từ booking thường đã chốt hóa đơn.
        // Lấy totalAmount: amountReceived có thể gồm tiền trả lại khách.
        long bookingCash = sumBooking("CASH", start, end, null);

        // Daily Visitor hiện thu tại quầy bằng tiền mặt lúc check-in.
        long dailyCash = sumDaily(start, end, null);

        long bankTransfer = sumBooking(
                "BANK_TRANSFER", start, end, null
        );

        long myBookingCash = sumBooking(
                "CASH", start, end, staffId
        );

        long myDailyCash = sumDaily(start, end, staffId);

        long myBankTransfer = sumBooking(
                "BANK_TRANSFER", start, end, staffId
        );

        long cashExpected = bookingCash + dailyCash;

        return new StaffCashSummary(
                today,
                cashExpected,
                bankTransfer,
                cashExpected + bankTransfer,
                myBookingCash + myDailyCash,
                myBankTransfer
        );
    }

    private long sumBooking(
            String method,
            LocalDateTime start,
            LocalDateTime end,
            Long staffId) {

        return entityManager.createQuery("""
                SELECT COALESCE(SUM(b.totalAmount), 0)
                FROM NormalBooking b
                WHERE b.status = 'COMPLETED'
                  AND b.paymentMethod = :method
                  AND b.completedAt >= :start
                  AND b.completedAt < :end
                  AND (:staffId IS NULL OR b.completedBy = :staffId)
                """, Long.class)
                .setParameter("method", method)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("staffId", staffId)
                .getSingleResult();
    }

    private long sumDaily(
            LocalDateTime start,
            LocalDateTime end,
            Long staffId) {

        return entityManager.createQuery("""
                SELECT COALESCE(SUM(p.amountPaid), 0)
                FROM DailyVisitorParticipant p
                WHERE p.paidAt >= :start
                  AND p.paidAt < :end
                  AND (:staffId IS NULL OR p.paidBy = :staffId)
                """, Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("staffId", staffId)
                .getSingleResult();
    }
}