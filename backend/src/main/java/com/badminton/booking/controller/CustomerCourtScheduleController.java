package com.badminton.booking.controller;

import com.badminton.booking.entity.Court;
import com.badminton.booking.entity.CourtMaintenance;
import com.badminton.booking.entity.CourtPrice;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.repository.CourtPriceRepository;
import com.badminton.booking.repository.CourtRepository;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Lịch công khai chỉ chứa sân, giá và khoảng bận; không trả dữ liệu khách hàng. */
@RestController
@RequestMapping("/api/courts/schedule")
public class CustomerCourtScheduleController {

  private final CourtRepository courts;
  private final CourtPriceRepository prices;
  private final EntityManager em;
  private final com.badminton.booking.service.CustomerBookingPolicy policy;

  public CustomerCourtScheduleController(
    CourtRepository courts,
    CourtPriceRepository prices,
    EntityManager em,
    com.badminton.booking.service.CustomerBookingPolicy policy
  ) {
    this.courts = courts;
    this.prices = prices;
    this.em = em;
    this.policy = policy;
  }

  public record BusyPeriod(LocalDateTime start, LocalDateTime end, String reason) {}

  public record Price(
    LocalTime openingTime,
    LocalTime closingTime,
    LocalTime peakStartTime,
    Long normalPricePerHour,
    Long peakPricePerHour
  ) {}

  public record CourtRow(
    Long id,
    String name,
    Long roomId,
    String roomName,
    String roomGroup,
    Long typeId,
    String typeName,
    String typeDescription,
    boolean active,
    boolean allowsPieces,
    Price price,
    List<BusyPeriod> busy
  ) {}

  public record Schedule(
    LocalDate date,
    LocalDateTime serverTime,
    LocalDate maxBookingDate,
    List<CourtRow> courts
  ) {}

  @GetMapping
  @Transactional(readOnly = true)
  public Schedule getSchedule(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
  ) {
    LocalDateTime start = date.atStartOfDay();
    LocalDateTime end = date.plusDays(1).atStartOfDay();
    List<NormalBooking> bookings = em
      .createQuery(
        """
        select b from NormalBooking b
        where b.bookingDate = :date and b.status not in ('CANCELLED', 'NO_SHOW')
        """,
        NormalBooking.class
      )
      .setParameter("date", date)
      .getResultList();
    List<CourtMaintenance> maintenance = em
      .createQuery(
        """
        select m from CourtMaintenance m
        where m.status in ('SCHEDULED', 'IN_PROGRESS')
        and m.startTime < :end and (m.endTime is null or m.endTime > :start)
        """,
        CourtMaintenance.class
      )
      .setParameter("start", start)
      .setParameter("end", end)
      .getResultList();
    var sessions = em
      .createQuery(
        "select s from DailyVisitorSession s where s.sessionDate = :date and s.status <> 'CANCELLED'",
        com.badminton.booking.entity.DailyVisitorSession.class
      )
      .setParameter("date", date)
      .getResultList();
    Map<Long, CourtPrice> byType = new HashMap<>();
    for (CourtPrice p : prices.findAll()) {
      if (Boolean.TRUE.equals(p.getActive())) byType.put(p.getCourtType().getId(), p);
    }
    List<CourtRow> rows = new ArrayList<>();
    for (Court c : courts.findAll()) {
      var room = c.getRoom();
      var type = room == null ? null : room.getCourtType();
      CourtPrice p = type == null ? null : byType.get(type.getId());
      Price price =
        p == null
          ? null
          : new Price(
              p.getOpeningTime(),
              p.getClosingTime(),
              p.getPeakStartTime(),
              p.getNormalPricePerHour(),
              p.getPeakPricePerHour()
            );
      List<BusyPeriod> busy = new ArrayList<>();
      for (NormalBooking b : bookings) {
        if (c.getId().equals(b.getCourt().getId())) {
          busy.add(
            new BusyPeriod(date.atTime(b.getStartTime()), date.atTime(b.getEndTime()), "Đã đặt")
          );
        }
      }
      for (CourtMaintenance m : maintenance) {
        if (c.getId().equals(m.getCourt().getId())) {
          busy.add(new BusyPeriod(m.getStartTime(), m.getEndTime(), "Bảo trì"));
        }
      }
      for (var session : sessions) {
        if (c.getId().equals(session.getSchedule().getCourt().getId())) {
          busy.add(
            new BusyPeriod(
              date.atTime(session.getStartTime()),
              date.atTime(session.getEndTime()),
              "Buổi Daily Visitor"
            )
          );
        }
      }
      rows.add(
        new CourtRow(
          c.getId(),
          c.getName(),
          room == null ? null : room.getId(),
          room == null ? "" : room.getName(),
          room == null ? "" : room.getRoomGroup(),
          type == null ? null : type.getId(),
          type == null ? "" : type.getName(),
          type == null ? "" : type.getDescription(),
          Boolean.TRUE.equals(c.getActive()) &&
            room != null &&
            Boolean.TRUE.equals(room.getActive()) &&
            type != null &&
            Boolean.TRUE.equals(type.getActive()),
          type != null && policy.allowsPieces(type.getName()),
          price,
          busy
        )
      );
    }
    rows.sort(Comparator.comparing(CourtRow::name));
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    return new Schedule(
      date,
      LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")),
      policy.lastBookingDate(today),
      rows
    );
  }
}
