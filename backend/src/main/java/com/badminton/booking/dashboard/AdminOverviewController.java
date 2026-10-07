package com.badminton.booking.dashboard;

import com.badminton.booking.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/overview")
public class AdminOverviewController {
    private final EntityManager em;
    public AdminOverviewController(EntityManager em) { this.em = em; }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> summary(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                      Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên được xem tổng quan");
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 365)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Khoảng ngày không hợp lệ, tối đa 366 ngày");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from); result.put("to", to);
        Map<String, Long> activity = new LinkedHashMap<>();
        String booking = " FROM NormalBooking b WHERE b.bookingDate >= :start AND b.bookingDate < :end";
        activity.put("totalNormalBookings", amount("SELECT COUNT(b)" + booking, from, to, true));
        for (String status : new String[]{"PENDING", "CHECKED_IN", "COMPLETED", "CANCELLED", "NO_SHOW"})
            activity.put(status, amount("SELECT COUNT(b)" + booking + ("PENDING".equals(status) ? " AND b.status IN ('PENDING', 'NO_SHOW_PENDING')" : " AND b.status = '" + status + "'"), from, to, true));
        activity.put("paidNormalBookings", amount("SELECT COUNT(b) FROM NormalBooking b WHERE b.paidAt >= :start AND b.paidAt < :end AND b.status = 'COMPLETED'", from, to, false));
        activity.put("totalDailyVisitorSessions", amount("SELECT COUNT(s) FROM DailyVisitorSession s WHERE s.sessionDate >= :start AND s.sessionDate < :end", from, to, true));
        activity.put("dailyVisitorCheckedInSlots", amount("SELECT COALESCE(SUM(p.checkedInSlots),0) FROM DailyVisitorParticipant p WHERE p.checkedInAt >= :start AND p.checkedInAt < :end", from, to, false));
        result.put("activity", activity);
        Map<String, Long> inventory = new LinkedHashMap<>();
        String received = " FROM InventoryBatch b WHERE b.receivedAt >= :start AND b.receivedAt < :end AND (b.status = 'RECEIVED' OR b.status IS NULL)";
        inventory.put("receivedBatches", amount("SELECT COUNT(b)" + received, from, to, false));
        inventory.put("receivedTubes", amount("SELECT COALESCE(SUM(b.quantityReceivedTubes),0)" + received, from, to, false));
        inventory.put("importValue", amount("SELECT COALESCE(SUM(b.quantityReceivedTubes * b.importPricePerTube),0)" + received, from, to, false));
        String issued = " FROM InventoryIssue i WHERE i.issuedAt >= :start AND i.issuedAt < :end AND i.cancelledAt IS NULL";
        inventory.put("issuedTubes", amount("SELECT COALESCE(SUM(i.quantityTubes),0)" + issued, from, to, false));
        inventory.put("issuedPieces", amount("SELECT COALESCE(SUM(i.quantityPieces),0)" + issued, from, to, false));
        result.put("inventoryPeriod", inventory);
        Map<String, Long> maintenance = new LinkedHashMap<>();
        maintenance.put("scheduled", amount("SELECT COUNT(m) FROM CourtMaintenance m WHERE m.startTime >= :start AND m.startTime < :end AND m.status <> 'CANCELLED'", from, to, false));
        maintenance.put("completed", amount("SELECT COUNT(m) FROM CourtMaintenance m WHERE m.completedAt >= :start AND m.completedAt < :end AND m.status = 'COMPLETED'", from, to, false));
        maintenance.put("missingCosts", amount("SELECT COUNT(m) FROM CourtMaintenance m WHERE m.completedAt >= :start AND m.completedAt < :end AND m.status = 'COMPLETED' AND m.maintenanceCost IS NULL", from, to, false));
        result.put("maintenancePeriod", maintenance);
        result.put("activeCourts", snapshot("SELECT COUNT(c) FROM Court c WHERE c.active = true"));
        result.put("inactiveCourts", snapshot("SELECT COUNT(c) FROM Court c WHERE c.active = false"));
        result.put("activeStaff", snapshot("SELECT COUNT(u) FROM User u WHERE u.role = 'STAFF' AND u.status = 'ACTIVE'"));
        return result;
    }
    private Long snapshot(String jpql) { return em.createQuery(jpql, Long.class).getSingleResult(); }
    private Long amount(String jpql, LocalDate from, LocalDate to, boolean dateOnly) {
        return em.createQuery(jpql, Long.class)
            .setParameter("start", dateOnly ? from : from.atStartOfDay())
            .setParameter("end", dateOnly ? to.plusDays(1) : to.plusDays(1).atStartOfDay())
            .getSingleResult();
    }
}
