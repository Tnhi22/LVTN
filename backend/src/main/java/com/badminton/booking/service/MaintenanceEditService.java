package com.badminton.booking.service;

import com.badminton.booking.dto.CourtMaintenanceRequest;
import com.badminton.booking.entity.*;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceEditService {
    private final EntityManager em;
    private final CourtRepository courts;
    private final UserRepository users;
    private final NormalBookingRepository bookings;
    private final BookingInventoryService inventory;

    public MaintenanceEditService(EntityManager em, CourtRepository courts, UserRepository users,
            NormalBookingRepository bookings, BookingInventoryService inventory) {
        this.em = em;
        this.courts = courts;
        this.users = users;
        this.bookings = bookings;
        this.inventory = inventory;
    }

    private User actor(Long id) {
        User user = users.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập"));
        if (!List.of("ADMIN", "STAFF").contains(user.getRole()) || !"ACTIVE".equals(user.getStatus()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ ADMIN/STAFF đang hoạt động được sửa hoặc hủy bảo trì");
        return user;
    }

    private CourtMaintenance locked(Long id, Long targetCourtId) {
        CourtMaintenance m = em.find(CourtMaintenance.class, id);
        if (m == null) throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch bảo trì");
        Long oldCourtId = m.getCourt().getId();
        // Lock courts in the same order as multi-court booking, before the maintenance row.
        for (Long courtId : new TreeSet<>(List.of(oldCourtId, targetCourtId == null ? oldCourtId : targetCourtId))) {
            courts.findByIdForBooking(courtId).orElseThrow(() ->
                    new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy sân"));
        }
        em.refresh(m, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(oldCourtId, m.getCourt().getId()))
            throw new BusinessException(HttpStatus.CONFLICT, "Lịch vừa được đổi sân. Tải lại rồi thử lại.");
        if (!List.of("SCHEDULED", "IN_PROGRESS", "PENDING_APPROVAL").contains(m.getStatus()))
            throw new BusinessException(HttpStatus.CONFLICT, "Lịch đã hoàn tất hoặc đã hủy, không thể thay đổi");
        return m;
    }

    private void audit(CourtMaintenance m, User actor, String action, LocalDateTime now) {
        String line = "[" + now + "] " + action + " — " + actor.getFullName() + " (#" + actor.getId() + ")";
        m.setRepairDetail((m.getRepairDetail() == null || m.getRepairDetail().isBlank() ? "" : m.getRepairDetail() + "\n") + line);
    }

    @Transactional
    public CourtMaintenance update(Long id, Long actorId, CourtMaintenanceRequest request) {
        User actor = actor(actorId);
        if (request == null || request.getCourtId() == null || request.getStartTime() == null
                || request.getReason() == null || request.getReason().isBlank()
                || request.getReason().length() > 1000 || request.getType() == null
                || !List.of("SCHEDULED", "EMERGENCY").contains(request.getType()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Chọn sân, loại, thời gian và lý do hợp lệ (tối đa 1000 ký tự)");
        CourtMaintenance m = locked(id, request.getCourtId());
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        boolean started = !m.getStartTime().isAfter(now) || !"SCHEDULED".equals(m.getStatus());
        if (started && (!Objects.equals(m.getCourt().getId(), request.getCourtId())
                || !Objects.equals(m.getType(), request.getType())
                || !Objects.equals(m.getStartTime(), request.getStartTime())))
            throw new BusinessException(HttpStatus.CONFLICT, "Lịch đã bắt đầu: không đổi sân, loại hoặc giờ bắt đầu; có thể sửa lý do, chi phí và giờ kết thúc");
        if (!started && request.getStartTime().isBefore(now))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Không chuyển lịch chưa bắt đầu về quá khứ");
        LocalDateTime start = request.getStartTime(), end = request.getEndTime();
        if (("SCHEDULED".equals(request.getType()) && end == null)
                || (end != null && (!end.isAfter(start) || (started && !end.isAfter(now)))))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Giờ kết thúc phải sau giờ bắt đầu và sau hiện tại nếu đang bảo trì");
        long cost = request.getMaintenanceCost() == null ? 0 : request.getMaintenanceCost();
        if (cost < 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Chi phí không được âm");
        Court court = em.find(Court.class, request.getCourtId());
        if (!Boolean.TRUE.equals(court.getActive()))
            throw new BusinessException(HttpStatus.CONFLICT, "Sân đã ngừng hoạt động");
        LocalDateTime until = end == null ? LocalDateTime.of(9999, 12, 31, 23, 59) : end;
        long conflicts = em.createQuery("SELECT COUNT(m) FROM CourtMaintenance m WHERE m.id <> :id AND m.court.id = :court AND m.status IN ('SCHEDULED','IN_PROGRESS','PENDING_APPROVAL') AND m.startTime < :end AND (m.endTime IS NULL OR m.endTime > :start)", Long.class)
                .setParameter("id", id).setParameter("court", court.getId()).setParameter("start", start)
                .setParameter("end", until).getSingleResult();
        if (conflicts > 0) throw new BusinessException(HttpStatus.CONFLICT, "Trùng lịch bảo trì khác, hãy chọn thời gian khác");
        List<NormalBooking> affected = em.createQuery("SELECT b FROM NormalBooking b WHERE b.court.id = :court AND b.status IN ('PENDING','NO_SHOW_PENDING','CHECKED_IN') AND b.bookingDate >= :first AND b.bookingDate <= :last ORDER BY b.id", NormalBooking.class)
                .setParameter("court", court.getId()).setParameter("first", start.toLocalDate())
                .setParameter("last", until.toLocalDate()).getResultList().stream()
                .filter(b -> b.getBookingDate().atTime(b.getStartTime()).isBefore(until)
                        && b.getBookingDate().atTime(b.getEndTime()).isAfter(start)).toList();
        boolean intervalChanged = !Objects.equals(m.getCourt().getId(), court.getId())
                || !Objects.equals(m.getStartTime(), start) || !Objects.equals(m.getEndTime(), end)
                || !Objects.equals(m.getType(), request.getType());
        if (intervalChanged && "SCHEDULED".equals(request.getType()) && !affected.isEmpty())
            throw new BusinessException(HttpStatus.CONFLICT, "Khung bảo trì có booking, chọn thời gian khác");
        String previous = "Sửa lịch: sân #" + m.getCourt().getId() + ", " + m.getStartTime() + " → " + m.getEndTime()
                + ", lý do: " + m.getReason() + ", chi phí: " + m.getMaintenanceCost();
        if (intervalChanged && "EMERGENCY".equals(request.getType())) {
            for (NormalBooking candidate : affected) {
                NormalBooking b = bookings.findByIdForSettlement(candidate.getId()).orElseThrow();
                if (!List.of("PENDING", "NO_SHOW_PENDING").contains(b.getStatus())) continue;
                b.setStatus("CANCELLED"); b.setCancelledAt(now);
                b.setCancelledByStaffId(actor.getId()); b.setCancelledByStaffName(actor.getFullName());
                inventory.releaseReservation(b);
                bookings.save(b);
            }
        }
        m.setCourt(court); m.setType(request.getType()); m.setReason(request.getReason().trim());
        m.setStartTime(start); m.setEndTime(end); m.setMaintenanceCost(cost);
        audit(m, actor, previous, now);
        return m;
    }

    @Transactional
    public CourtMaintenance cancel(Long id, Long actorId, String reason) {
        User actor = actor(actorId);
        if (reason == null || reason.isBlank() || reason.length() > 1000)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Nhập lý do hủy (tối đa 1000 ký tự)");
        CourtMaintenance m = locked(id, null);
        m.setStatus("CANCELLED");
        audit(m, actor, "Hủy lịch: " + reason.trim(), LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        // No booking is restored. Other active maintenance intervals keep blocking the court.
        return m;
    }
}
