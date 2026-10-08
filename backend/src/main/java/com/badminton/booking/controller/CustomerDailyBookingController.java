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
    Long totalAmount,
    boolean daily
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
          p.getStatus(),
          s.getStatus(),
          p.getAmountDue(),
          true
        );
      })
      .toList();
  }
}
