package com.badminton.booking.service;

import com.badminton.booking.dto.BookingBillItem;
import com.badminton.booking.dto.BookingReceipt;
import com.badminton.booking.dto.SettlementRequest;
import com.badminton.booking.entity.InventoryIssue;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.InventoryIssueRepository;
import com.badminton.booking.repository.NormalBookingRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingSettlementService {

  private final NormalBookingRepository bookings;
  private final InventoryIssueRepository issues;

  public BookingSettlementService(
    NormalBookingRepository bookings,
    InventoryIssueRepository issues
  ) {
    this.bookings = bookings;
    this.issues = issues;
  }

  @Transactional(readOnly = true)
  public BookingReceipt preview(Long id) {
    NormalBooking booking = bookings
      .findById(id)
      .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));

    return receipt(booking);
  }

  // Nút "Hoàn thành sân": chỉ bấm khi đã hết giờ chơi.
  @Transactional
  public BookingReceipt complete(Long id, Long staffId) {
    NormalBooking booking = bookings
      .findByIdForSettlement(id)
      .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));

    if (!"CHECKED_IN".equals(booking.getStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, "Chỉ booking đang chơi mới được hoàn thành");
    }

    LocalDateTime bookingEnd = LocalDateTime.of(booking.getBookingDate(), booking.getEndTime());

    if (LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).isBefore(bookingEnd)) {
      throw new BusinessException(
        HttpStatus.CONFLICT,
        "Chưa hết giờ chơi, chưa thể hoàn thành sân"
      );
    }

    booking.setStatus("COMPLETED");
    booking.setCompletedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
    booking.setCompletedBy(staffId);

    // Lúc này chưa ghi nhận thanh toán.
    return receipt(bookings.save(booking));
  }

  // Nút "Đã nhận tiền": chỉ dùng sau khi sân đã hoàn thành.
  @Transactional
  public BookingReceipt settle(Long id, SettlementRequest request, Long staffId) {
    NormalBooking booking = bookings
      .findByIdForSettlement(id)
      .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));

    if (!"COMPLETED".equals(booking.getStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, "Cần hoàn thành sân trước khi thu tiền");
    }

    if (booking.getPaidAt() != null) {
      throw new BusinessException(HttpStatus.CONFLICT, "Booking đã được ghi nhận thanh toán");
    }

    long shuttle = booking.getShuttlecockAmount() == null ? 0L : booking.getShuttlecockAmount();

    Long total = booking.getTotalAmount();

    if (total == null || total < shuttle || shuttle < 0) {
      throw new BusinessException(HttpStatus.CONFLICT, "Booking chưa có tổng tiền hợp lệ");
    }

    if (request == null || request.paymentMethod() == null || request.paymentMethod().isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "Cần chọn phương thức thanh toán");
    }

    String method = request.paymentMethod().trim().toUpperCase(Locale.ROOT);

    if (!method.equals("CASH") && !method.equals("BANK_TRANSFER")) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Phương thức phải là CASH hoặc BANK_TRANSFER"
      );
    }

    long received = request.amountReceived() == null ? total : request.amountReceived();

    if (received < total || (method.equals("BANK_TRANSFER") && received != total)) {
      throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "Số tiền nhận chưa hợp lệ so với tổng hóa đơn"
      );
    }

    booking.setPaymentMethod(method);
    booking.setAmountReceived(received);
    booking.setPaidAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
    booking.setPaidBy(staffId);

    return receipt(bookings.save(booking));
  }

  private BookingBillItem item(InventoryIssue issue) {
    return new BookingBillItem(
      issue.getId(),
      issue.getProduct().getId(),
      issue.getProduct().getName(),
      issue.getQuantityTubes(),
      issue.getQuantityPieces(),
      issue.getUnitPrice(),
      issue.getTotalAmount(),
      issue.getIssuedAt(),
      issue.getCancelledAt() != null,
      issue.getCancelledAt(),
      issue.getCancelledBy(),
      issue.getCancelReason()
    );
  }

  private BookingReceipt receipt(NormalBooking booking) {
    long shuttle = booking.getShuttlecockAmount() == null ? 0L : booking.getShuttlecockAmount();

    Long total = booking.getTotalAmount();
    Long court = total == null ? null : total - shuttle;

    Long change =
      total == null || booking.getAmountReceived() == null
        ? null
        : booking.getAmountReceived() - total;

    List<BookingBillItem> items = new ArrayList<>(
      issues
        .findByIssueTypeInAndReferenceIdOrderByIssuedAtAscIdAsc(
          List.of("NORMAL_BOOKING", "NORMAL_BOOKING_ADDON", "NORMAL_BOOKING_ADDON_CANCELLED"),
          booking.getId()
        )
        .stream()
        .map(this::item)
        .toList()
    );
    if (
      !Boolean.TRUE.equals(booking.getShuttlecockIssued()) &&
      booking.getShuttlecockProduct() != null
    ) {
      items.add(
        0,
        new BookingBillItem(
          null,
          booking.getShuttlecockProduct().getId(),
          booking.getShuttlecockProduct().getName(),
          booking.getShuttlecockQuantityTubes(),
          booking.getShuttlecockQuantityPieces(),
          booking.getShuttlecockUnitPrice(),
          shuttle,
          null,
          false,
          null,
          null,
          null
        )
      );
    }

    return new BookingReceipt(
      booking.getId(),
      booking.getStatus(),
      court,
      shuttle,
      total,
      booking.getPaymentMethod(),
      booking.getAmountReceived(),
      change,
      booking.getCompletedAt(),
      booking.getCompletedBy(),
      items
    );
  }
}
