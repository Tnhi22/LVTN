package com.badminton.booking.controller;

import com.badminton.booking.entity.DailyVisitorSession;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tổng hợp công khai: không trả tên, số điện thoại hoặc ID của người tham gia. */
@RestController
@RequestMapping("/api/courts/home-status")
public class CustomerHomeStatusController {

  @PersistenceContext
  private EntityManager em;

  public record SessionRow(
    Long id,
    String courtName,
    String skillLevel,
    LocalTime startTime,
    LocalTime endTime,
    int capacity,
    int remainingSlots
  ) {}

  public record HomeStatus(LocalDate date, LocalDateTime updatedAt, List<SessionRow> sessions) {}

  @GetMapping
  @Transactional(readOnly = true)
  public HomeStatus getStatus() {
    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    List<DailyVisitorSession> sessions = em
      .createQuery(
        "select s from DailyVisitorSession s join fetch s.schedule d join fetch d.court c " +
          "where s.sessionDate = :day and s.status = 'OPEN' " +
          "and s.startTime > :time and d.active = true order by s.startTime",
        DailyVisitorSession.class
      )
      .setParameter("day", now.toLocalDate())
      .setParameter("time", now.toLocalTime())
      .getResultList();
    if (sessions.isEmpty()) return new HomeStatus(now.toLocalDate(), now, List.of());
    List<Long> ids = sessions.stream().map(DailyVisitorSession::getId).toList();
    List<Object[]> counts = em
      .createQuery(
        "select p.session.id, sum(p.slotCount) from DailyVisitorParticipant p " +
          "where p.session.id in :ids and p.status in ('CONFIRMED', 'CHECKED_IN') " +
          "group by p.session.id",
        Object[].class
      )
      .setParameter("ids", ids)
      .getResultList();
    Map<Long, Long> occupied = counts
      .stream()
      .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
    List<SessionRow> rows = sessions
      .stream()
      .map(s -> {
        int capacity = s.getMaxParticipants() == null ? 0 : s.getMaxParticipants();
        int remaining = (int) Math.max(0L, capacity - occupied.getOrDefault(s.getId(), 0L));
        return new SessionRow(
          s.getId(),
          s.getSchedule().getCourt().getName(),
          s.getSchedule().getSkillLevel(),
          s.getStartTime(),
          s.getEndTime(),
          capacity,
          remaining
        );
      })
      .toList();
    return new HomeStatus(now.toLocalDate(), now, rows);
  }
}
