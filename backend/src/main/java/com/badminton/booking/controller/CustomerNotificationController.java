package com.badminton.booking.controller;

import com.badminton.booking.dto.DailyVisitorWaitlistResponse;
import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.entity.DailyVisitorWaitlist;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.WaitlistStatus;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.service.DailyVisitorWaitlistService;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Chỉ trả cập nhật của tài khoản hiện tại, không trả thông tin khách khác. */
@RestController
@RequestMapping("/api/customer/notifications")
public class CustomerNotificationController {

  private final EntityManager em;
  private final DailyVisitorWaitlistService waitlistService;

  public CustomerNotificationController(
    EntityManager em,
    DailyVisitorWaitlistService waitlistService
  ) {
    this.em = em;
    this.waitlistService = waitlistService;
  }

  @GetMapping("/waitlists")
  @Transactional
  public List<DailyVisitorWaitlistResponse> getWaiting(
    @RequestParam(defaultValue = "false") boolean includeHistory,
    @AuthenticationPrincipal Jwt jwt
  ) {
    Long userId = requireCustomer(jwt);
    var entries = em
      .createQuery(
        "select w from DailyVisitorWaitlist w where w.user.id = :id and w.status in :statuses order by w.session.id asc",
        DailyVisitorWaitlist.class
      )
      .setParameter("id", userId)
      .setParameter(
        "statuses",
        includeHistory
          ? List.of(WaitlistStatus.values())
          : List.of(WaitlistStatus.WAITING, WaitlistStatus.OFFERED)
      )
      .getResultList();
    List<DailyVisitorWaitlistResponse> result = new ArrayList<>();
    for (var w : entries)
      result.add(waitlistService.getMyStatus(w.getSession().getId(), userId));
    return result;
  }

  public record Notice(
    String id,
    String title,
    String message,
    LocalDateTime occurredAt,
    String target,
    Long sessionId,
    boolean actionable,
    LocalDateTime offerExpiresAt
  ) {}

  public record Feed(LocalDateTime serverTime, List<Notice> items) {}

  @GetMapping
  @Transactional(readOnly = true)
  public Feed getMine(@AuthenticationPrincipal Jwt jwt) {
    Long userId = requireCustomer(jwt);
    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    List<Notice> items = new ArrayList<>();
    var bookings = em
      .createQuery(
        "select b from NormalBooking b where b.user.id = :id order by b.createdAt desc",
        NormalBooking.class
      )
      .setParameter("id", userId)
      .setMaxResults(100)
      .getResultList();
    for (var b : bookings) {
      String detail =
        b.getCourt().getName() +
        " · " +
        b.getBookingDate() +
        " · " +
        slot(b.getStartTime(), b.getEndTime());
      items.add(
        new Notice(
          "booking:" + b.getId() + ":created",
          "Đặt sân thành công",
          detail,
          b.getCreatedAt(),
          "history",
          null,
          false,
          null
        )
      );
      if ("CANCELLED".equals(b.getStatus())) {
        items.add(
          new Notice(
            "booking:" + b.getId() + ":cancelled",
            "Booking đã được hủy",
            detail,
            b.getCancelledAt(),
            "history",
            null,
            false,
            null
          )
        );
      }
    }
    var participants = em
      .createQuery(
        "select p from DailyVisitorParticipant p where p.user.id = :id order by p.registeredAt desc",
        DailyVisitorParticipant.class
      )
      .setParameter("id", userId)
      .setMaxResults(100)
      .getResultList();
    for (var p : participants) {
      var s = p.getSession();
      String detail =
        "Daily " +
        s.getSchedule().getSkillLevel() +
        " · " +
        s.getSchedule().getCourt().getName() +
        " · " +
        s.getSessionDate() +
        " · " +
        slot(s.getStartTime(), s.getEndTime());
      items.add(
        new Notice(
          "daily:" + p.getId() + ":created",
          "Đăng ký Daily thành công",
          detail,
          p.getRegisteredAt(),
          "history",
          s.getId(),
          false,
          null
        )
      );
      if (
        "CANCELLED".equals(s.getStatus()) || "CANCELLED".equals(p.getStatus())
      ) {
        // Entity hiện tại không lưu thời điểm hủy Daily: không giả lập một thời điểm.
        items.add(
          new Notice(
            "daily:" + p.getId() + ":cancelled",
            "CANCELLED".equals(s.getStatus())
              ? "Ca Daily đã bị hủy"
              : "Đã hủy đăng ký Daily",
            detail +
              ". " +
              ("CANCELLED".equals(s.getStatus())
                ? cancellationMessage(
                    s.getCancelReason(),
                    s.getMinParticipants()
                  )
                : "Lượt đăng ký đã được hủy."),
            null,
            "history",
            s.getId(),
            false,
            null
          )
        );
      }
    }
    var entries = em
      .createQuery(
        "select w from DailyVisitorWaitlist w where w.user.id = :id order by w.createdAt desc, w.id desc",
        DailyVisitorWaitlist.class
      )
      .setParameter("id", userId)
      .setMaxResults(100)
      .getResultList();
    for (var w : entries) {
      var s = w.getSession();
      String detail =
        "Daily " +
        s.getSchedule().getSkillLevel() +
        " · " +
        s.getSessionDate() +
        " · " +
        slot(s.getStartTime(), s.getEndTime());
      items.add(
        new Notice(
          "waiting:" + w.getId() + ":joined:" + w.getCreatedAt(),
          "Đã tham gia danh sách chờ",
          detail + ". Backend ưu tiên theo thứ tự đăng ký.",
          w.getCreatedAt(),
          "my-bookings",
          s.getId(),
          false,
          null
        )
      );
      boolean usable =
        w.getStatus() == WaitlistStatus.OFFERED &&
        w.getOfferExpiresAt() != null &&
        now.isBefore(w.getOfferExpiresAt()) &&
        now.isBefore(s.getSessionDate().atTime(s.getStartTime())) &&
        !"CANCELLED".equals(s.getStatus()) &&
        !"CLOSED".equals(s.getStatus());
      if (usable) {
        items.add(
          new Notice(
            "waiting:" + w.getId() + ":offered:" + w.getOfferedAt(),
            "Đến lượt bạn nhận slot!",
            detail + ". Xác nhận trước khi lời mời hết hạn để vào chơi.",
            w.getOfferedAt(),
            "my-bookings",
            s.getId(),
            true,
            w.getOfferExpiresAt()
          )
        );
      } else if (
        w.getStatus() != WaitlistStatus.WAITING ||
        "CANCELLED".equals(s.getStatus())
      ) {
        String title = switch (w.getStatus()) {
          case CONFIRMED -> "Đã xác nhận slot từ danh sách chờ";
          case EXPIRED, OFFERED -> "Lời mời nhận slot đã hết hạn";
          case UNAVAILABLE -> "Ca Daily không còn khả dụng";
          case DECLINED, CANCELLED -> "Đã rời danh sách chờ";
          default -> "Cập nhật danh sách chờ";
        };
        boolean sessionCancelled = "CANCELLED".equals(s.getStatus());
        if (sessionCancelled) title = "Ca Daily trong danh sách chờ đã bị hủy";
        items.add(
          new Notice(
            "waiting:" +
              w.getId() +
              ":" +
              (sessionCancelled ? "SESSION_CANCELLED" : w.getStatus()) +
              ":" +
              w.getCreatedAt(),
            title,
            detail +
              (sessionCancelled
                ? ". " +
                  cancellationMessage(
                    s.getCancelReason(),
                    s.getMinParticipants()
                  )
                : ""),
            w.getResolvedAt(),
            "my-bookings",
            s.getId(),
            false,
            null
          )
        );
      }
    }
    items.sort(
      Comparator.comparing(Notice::actionable)
        .reversed()
        .thenComparing(
          Notice::occurredAt,
          Comparator.nullsFirst(Comparator.reverseOrder())
        )
        .thenComparing(Notice::id)
    );
    return new Feed(now, items.stream().limit(100).toList());
  }

  private static String cancellationMessage(String reason, Integer minimum) {
    String threshold =
      minimum == null ? "" : " (tối thiểu " + minimum + " người)";
    if ("NOT_ENOUGH_REGISTERED_PLAYERS".equals(reason)) return (
      "Ca tự động hủy vì không đủ người đăng ký" + threshold + "."
    );
    if ("NOT_ENOUGH_CHECKED_IN_PLAYERS".equals(reason)) return (
      "Ca tự động hủy vì không đủ người đến sân check-in" + threshold + "."
    );
    return reason == null || reason.isBlank()
      ? "Ca đã bị hủy. Vui lòng liên hệ quầy để biết thêm thông tin."
      : "Lý do hủy: " + reason;
  }

  private static String slot(LocalTime start, LocalTime end) {
    return (
      start.toString().substring(0, 5) + " – " + end.toString().substring(0, 5)
    );
  }

  private static Long requireCustomer(Jwt jwt) {
    if (
      jwt == null || !"CUSTOMER".equals(jwt.getClaimAsString("role"))
    ) throw new BusinessException(
      HttpStatus.FORBIDDEN,
      "Chỉ khách hàng được xem thông báo của mình"
    );
    try {
      return Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException e) {
      throw new BusinessException(
        HttpStatus.UNAUTHORIZED,
        "Phiên đăng nhập không hợp lệ"
      );
    }
  }
}
