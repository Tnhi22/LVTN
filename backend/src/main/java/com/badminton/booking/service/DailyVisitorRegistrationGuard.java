package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Shared guard for direct registration and accepting a waitlist offer. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DailyVisitorRegistrationGuard {

  @PersistenceContext
  private EntityManager em;

  /** Always lock the account BEFORE the session; keep the lock until commit. */
  public void lockAccount(Long userId) {
    if (userId == null || userId <= 0) {
      throw new BusinessException(
        HttpStatus.UNAUTHORIZED,
        "Không xác định được tài khoản đăng nhập"
      );
    }
    User user = em.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
    if (user == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng");
    }
  }

  /** Adjacent sessions are allowed: [start, end) intervals do not overlap at the boundary. */
  public void requireNoOverlap(Long userId, DailyVisitorSession target) {
    Long conflicts = em
      .createQuery(
        """
        select count(p.id) from DailyVisitorParticipant p
        join p.session s
        where p.user.id = :userId
          and p.status in ('CONFIRMED', 'CHECKED_IN')
          and s.status <> 'CANCELLED'
          and s.sessionDate = :date
          and s.startTime < :endTime
          and s.endTime > :startTime
        """,
        Long.class
      )
      .setParameter("userId", userId)
      .setParameter("date", target.getSessionDate())
      .setParameter("startTime", target.getStartTime())
      .setParameter("endTime", target.getEndTime())
      .getSingleResult();
    if (conflicts > 0) {
      throw new BusinessException(
        HttpStatus.CONFLICT,
        "Bạn đã đăng ký một ca Daily trùng giờ. Mỗi tài khoản chỉ được chơi một ca trong cùng khoảng thời gian, kể cả khác trình độ TBY, TB hoặc TB+."
      );
    }
  }
}
