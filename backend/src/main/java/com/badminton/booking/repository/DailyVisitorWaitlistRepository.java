package com.badminton.booking.repository;

import com.badminton.booking.entity.DailyVisitorWaitlist;
import com.badminton.booking.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyVisitorWaitlistRepository
        extends JpaRepository<DailyVisitorWaitlist, Long> {

    // Mỗi tài khoản có một bản ghi chờ trong một buổi.
    Optional<DailyVisitorWaitlist> findBySession_IdAndUser_Id(
            Long sessionId,
            Long userId
    );

    // Xếp hàng theo thời điểm tham gia, sau đó theo ID.
    List<DailyVisitorWaitlist>
    findBySession_IdAndStatusOrderByCreatedAtAscIdAsc(
            Long sessionId,
            WaitlistStatus status
    );

    // Lấy người đang chờ đầu tiên.
    Optional<DailyVisitorWaitlist>
    findFirstBySession_IdAndStatusOrderByCreatedAtAscIdAsc(
            Long sessionId,
            WaitlistStatus status
    );

    // Đếm lời mời đang giữ slot.
    long countBySession_IdAndStatus(
            Long sessionId,
            WaitlistStatus status
    );

    // Lấy các lượt đang chờ hoặc đang được mời.
    List<DailyVisitorWaitlist>
    findBySession_IdAndStatusInOrderByCreatedAtAscIdAsc(
            Long sessionId,
            Collection<WaitlistStatus> statuses
    );

    // Khách xem lịch sử danh sách chờ của mình.
    List<DailyVisitorWaitlist>
    findByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    // Các buổi cần được xử lý lời mời và thời hạn.
    @Query("""
            SELECT DISTINCT w.session.id
            FROM DailyVisitorWaitlist w
            WHERE w.status IN :statuses
            """)
    List<Long> findSessionIdsByStatuses(
            @org.springframework.data.repository.query.Param("statuses")
            Collection<WaitlistStatus> statuses
    );

    // Đếm tổng người đang chờ và đang được mời.
    long countBySession_IdAndStatusIn(
            Long sessionId,
            Collection<WaitlistStatus> statuses
    );
}