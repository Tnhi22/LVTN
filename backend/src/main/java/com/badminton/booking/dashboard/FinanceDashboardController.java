package com.badminton.booking.dashboard;

import com.badminton.booking.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/finance")
public class FinanceDashboardController {

    private final EntityManager entityManager;

    public FinanceDashboardController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public record FinanceSummary(
            LocalDate fromDate,
            LocalDate toDate,
            Long normalBookingRevenue,
            Long dailyVisitorRevenue,
            Long totalRevenue,
            Long maintenanceExpense,
            Long balance
    ) {}

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public FinanceSummary getSummary(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {

        if (from.isAfter(to)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày bắt đầu không được sau ngày kết thúc"
            );
        }

        // from 00:00:00 <= thời điểm ghi nhận < ngày sau to 00:00:00
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        // Chỉ tính booking đã chốt hóa đơn/thu tiền.
        Long normalRevenue = entityManager.createQuery("""
                SELECT COALESCE(SUM(b.totalAmount), 0)
                FROM NormalBooking b
                WHERE b.status = 'COMPLETED'
                  AND b.completedAt >= :start
                  AND b.completedAt < :end
                """, Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();

        // Daily Visitor thu tiền khi check-in.
        Long dailyRevenue = entityManager.createQuery("""
                SELECT COALESCE(SUM(p.amountPaid), 0)
                FROM DailyVisitorParticipant p
                WHERE p.paidAt >= :start
                  AND p.paidAt < :end
                """, Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();

        // Ghi chi phí vào ngày hoàn tất sửa chữa.
        Long maintenanceExpense = entityManager.createQuery("""
                SELECT COALESCE(SUM(m.maintenanceCost), 0)
                FROM CourtMaintenance m
                WHERE m.status = 'COMPLETED'
                  AND m.completedAt >= :start
                  AND m.completedAt < :end
                """, Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();

        long totalRevenue = normalRevenue + dailyRevenue;

        return new FinanceSummary(
                from,
                to,
                normalRevenue,
                dailyRevenue,
                totalRevenue,
                maintenanceExpense,
                totalRevenue - maintenanceExpense
        );
    }
}