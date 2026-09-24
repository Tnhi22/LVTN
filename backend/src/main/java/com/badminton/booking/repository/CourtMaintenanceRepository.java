package com.badminton.booking.repository;

import com.badminton.booking.entity.CourtMaintenance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CourtMaintenanceRepository
        extends JpaRepository<CourtMaintenance, Long> {

    // Xem toàn bộ lịch sử bảo trì/sự cố của một sân
    List<CourtMaintenance>
    findByCourtIdOrderByStartTimeDesc(
            Long courtId
    );

    // Xem các bảo trì/sự cố theo trạng thái
    List<CourtMaintenance>
    findByStatusInOrderByStartTimeAsc(
            List<String> statuses
    );

    /*
     * Kiểm tra một khoảng thời gian có trùng với
     * bảo trì/sự cố đang được lên lịch hoặc xử lý không.
     *
     * endTime null nghĩa là sân đang bị khóa
     * chưa xác định thời gian mở lại.
     */
    @Query("""
            SELECT CASE
                WHEN COUNT(m) > 0 THEN true
                ELSE false
            END
            FROM CourtMaintenance m
            WHERE m.court.id = :courtId
              AND m.status IN (
                    'SCHEDULED',
                    'IN_PROGRESS'
              )
              AND m.startTime < :endTime
              AND (
                    m.endTime IS NULL
                    OR m.endTime > :startTime
              )
            """)
    boolean existsActiveMaintenanceOverlap(
            @Param("courtId") Long courtId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // Lấy chi tiết các lịch bảo trì bị trùng
    @Query("""
            SELECT m
            FROM CourtMaintenance m
            WHERE m.court.id = :courtId
              AND m.status IN (
                    'SCHEDULED',
                    'IN_PROGRESS'
              )
              AND m.startTime < :endTime
              AND (
                    m.endTime IS NULL
                    OR m.endTime > :startTime
              )
            ORDER BY m.startTime ASC
            """)
    List<CourtMaintenance>
    findActiveMaintenanceOverlaps(
            @Param("courtId") Long courtId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}