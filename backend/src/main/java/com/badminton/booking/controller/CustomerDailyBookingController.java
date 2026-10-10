package com.badminton.booking.controller;

import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/daily-visitor-participants/my")
public class CustomerDailyBookingController {

  private final EntityManager em;

  public CustomerDailyBookingController(EntityManager em) {
    this.em = em;
  }

  public record CourtName(String name) {}

  public record MyDailyBooking(
    Long id,
    CourtName court,
    LocalDate bookingDate,
    LocalTime startTime,
    LocalTime endTime,
    String status,
    String sessionStatus,
    String sessionCancelReason,
    Integer minParticipants,
    Long totalAmount,
    boolean daily,
    Long registeredSlots,
    Long checkedInSlots,
    Integer maxParticipants
  ) {}

  @GetMapping
  @Transactional(readOnly = true)
  public List<MyDailyBooking> getMine(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null || !"CUSTOMER".equals(jwt.getClaimAsString("role"))) {
      throw new BusinessException(
        HttpStatus.FORBIDDEN,
        "Chỉ khách hàng được xem lịch đăng ký của mình"
      );
    }
    return em
      .createQuery(
        "select p from DailyVisitorParticipant p where p.user.id = :id order by p.registeredAt desc",
        DailyVisitorParticipant.class
      )
      .setParameter("id", Long.valueOf(jwt.getSubject()))
      .getResultList()
      .stream()
      .map(p -> {
        var s = p.getSession();
        return new MyDailyBooking(
          p.getId(),
          new CourtName(s.getSchedule().getCourt().getName()),
          s.getSessionDate(),
          s.getStartTime(),
          s.getEndTime(),
          "CANCELLED".equals(s.getStatus()) ? "CANCELLED" : p.getStatus(),
          s.getStatus(),
          s.getCancelReason(),
          s.getMinParticipants(),
          p.getAmountDue(),
          true,
          em
            .createQuery(
              "select coalesce(sum(p.slotCount), 0) from DailyVisitorParticipant p where p.session.id = :sessionId and p.status <> 'CANCELLED'",
              Long.class
            )
            .setParameter("sessionId", s.getId())
            .getSingleResult(),
          em
            .createQuery(
              "select coalesce(sum(p.checkedInSlots), 0) from DailyVisitorParticipant p where p.session.id = :sessionId and p.status <> 'CANCELLED'",
              Long.class
            )
            .setParameter("sessionId", s.getId())
            .getSingleResult(),
          s.getMaxParticipants()
        );
      })
      .toList();
  }
}
