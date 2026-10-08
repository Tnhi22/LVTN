package com.badminton.booking.controller;

import com.badminton.booking.entity.DailyVisitorSession;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courts/daily-options")
public class CustomerDailyOptionsController {

  @PersistenceContext
  private EntityManager em;

  private final com.badminton.booking.service.DailyVisitorFeePolicy feePolicy;

  public CustomerDailyOptionsController(
    com.badminton.booking.service.DailyVisitorFeePolicy feePolicy
  ) {
    this.feePolicy = feePolicy;
  }

  public record CourtRow(Long id, String name) {}

  public record ScheduleRow(Long id, CourtRow court, String skillLevel, Long fixedFee) {}

  public record SessionRow(
    Long id,
    ScheduleRow schedule,
    LocalDate sessionDate,
    LocalTime startTime,
    LocalTime endTime,
    String status,
    Integer maxParticipants,
    Long fixedFee,
    int remainingSlots
  ) {}

  public record Options(LocalDate date, LocalDateTime serverTime, List<SessionRow> sessions) {}

  @GetMapping
  @Transactional(readOnly = true)
  public Options get(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    List<DailyVisitorSession> sessions = em
      .createQuery(
        "select s from DailyVisitorSession s join fetch s.schedule d join fetch d.court c " +
          "join fetch c.room r join fetch r.courtType t where s.sessionDate = :day " +
          "and d.active = true and c.active = true and r.active = true and t.active = true " +
          "order by s.startTime",
        DailyVisitorSession.class
      )
      .setParameter("day", date)
      .getResultList();
    Map<Long, Long> occupied = new HashMap<>();
    if (!sessions.isEmpty()) {
      List<Object[]> counts = em
        .createQuery(
          "select p.session.id, sum(p.slotCount) from DailyVisitorParticipant p " +
            "where p.session.id in :ids and p.status in ('CONFIRMED', 'CHECKED_IN') " +
            "group by p.session.id",
          Object[].class
        )
        .setParameter("ids", sessions.stream().map(DailyVisitorSession::getId).toList())
        .getResultList();
      occupied = counts
        .stream()
        .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
    }
    Map<Long, Long> held = new HashMap<>();
    if (!sessions.isEmpty()) {
      List<Object[]> offers = em
        .createQuery(
          "select w.session.id, count(w.id) from DailyVisitorWaitlist w " +
            "where w.session.id in :ids and w.status = :status group by w.session.id",
          Object[].class
        )
        .setParameter("ids", sessions.stream().map(DailyVisitorSession::getId).toList())
        .setParameter("status", com.badminton.booking.entity.WaitlistStatus.OFFERED)
        .getResultList();
      held = offers
        .stream()
        .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
    }
    Map<Long, Long> heldCounts = held;
    Map<Long, Long> counts = occupied;
    List<SessionRow> rows = sessions
      .stream()
      .map(s -> {
        var d = s.getSchedule();
        int remaining = (int) Math.max(
          0L,
          (s.getMaxParticipants() == null ? 0 : s.getMaxParticipants()) -
            counts.getOrDefault(s.getId(), 0L) -
            heldCounts.getOrDefault(s.getId(), 0L)
        );
        Long fee = feePolicy.feePerPerson(s);
        return new SessionRow(
          s.getId(),
          new ScheduleRow(
            d.getId(),
            new CourtRow(d.getCourt().getId(), d.getCourt().getName()),
            d.getSkillLevel(),
            fee
          ),
          s.getSessionDate(),
          s.getStartTime(),
          s.getEndTime(),
          s.getStatus(),
          s.getMaxParticipants(),
          fee,
          remaining
        );
      })
      .toList();
    return new Options(date, LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")), rows);
  }
}
