package com.badminton.booking.service;

import com.badminton.booking.dto.*;
import com.badminton.booking.entity.*;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingOperationsService {

  @PersistenceContext
  private EntityManager em;

  private final NormalBookingRepository bookings;
  private final CourtRepository courts;
  private final CourtPriceRepository prices;
  private final CourtMaintenanceRepository maintenance;
  private final BookingInventoryService reservations;
  private final BookingBillService bills;
  private final InventoryService inventory;
  private final CustomerBookingPolicy policy;

  public BookingOperationsService(
    NormalBookingRepository bookings,
    CourtRepository courts,
    CourtPriceRepository prices,
    CourtMaintenanceRepository maintenance,
    BookingInventoryService reservations,
    BookingBillService bills,
    InventoryService inventory,
    CustomerBookingPolicy policy
  ) {
    this.bookings = bookings;
    this.courts = courts;
    this.prices = prices;
    this.maintenance = maintenance;
    this.reservations = reservations;
    this.bills = bills;
    this.inventory = inventory;
    this.policy = policy;
  }

  public record EditRequest(
    Long courtId,
    LocalDate bookingDate,
    LocalTime startTime,
    LocalTime endTime
  ) {}

  public record TubeRequest(Long productId, Integer quantityTubes) {}

  public record SaleRequest(
    Long productId,
    Integer quantityTubes,
    Integer quantityPieces,
    Long bookingId,
    String paymentMethod,
    Long amountReceived,
    String customerName,
    String customerPhone
  ) {}

  public record ProductOption(
    Long id,
    String name,
    Long tubePrice,
    int availableQuantityTubes,
    Long piecePrice
  ) {}

  public record PriceOption(
    LocalTime openingTime,
    LocalTime closingTime,
    LocalTime peakStartTime,
    Long normalPricePerHour,
    Long peakPricePerHour
  ) {}

  private PriceOption priceOption(CourtPrice p) {
    return p == null
      ? null
      : new PriceOption(
          p.getOpeningTime(),
          p.getClosingTime(),
          p.getPeakStartTime(),
          p.getNormalPricePerHour(),
          p.getPeakPricePerHour()
        );
  }

  public record CourtOption(
    Long id,
    String name,
    String roomName,
    String typeName,
    PriceOption price
  ) {}

  public record Options(
    List<ProductOption> products,
    List<CourtOption> courts
  ) {}

  public record SaleReceipt(
    Long id,
    String issueCode,
    Long bookingId,
    String productName,
    Integer quantityTubes,
    Integer quantityPieces,
    Long unitPrice,
    Long totalAmount,
    String paymentMethod,
    Long amountReceived,
    Long changeAmount,
    LocalDateTime paidAt
  ) {}

  private LocalDateTime now() {
    return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
  }

  private BusinessException conflict(String message) {
    return new BusinessException(HttpStatus.CONFLICT, message);
  }

  private NormalBooking locked(Long id) {
    return bookings
      .findByIdForSettlement(id)
      .orElseThrow(() -> conflict("Không tìm thấy booking"));
  }

  @Transactional(readOnly = true)
  public Options options() {
    var products = em
      .createQuery(
        "select p from Product p where p.active = true order by p.name",
        Product.class
      )
      .getResultList()
      .stream()
      .map(p ->
        new ProductOption(
          p.getId(),
          p.getName(),
          p.getTubePrice(),
          p.getAvailableQuantityTubes(),
          p.getPiecePrice()
        )
      )
      .toList();
    var courtOptions = courts
      .findAll()
      .stream()
      .filter(
        c ->
          Boolean.TRUE.equals(c.getActive()) &&
          c.getRoom() != null &&
          Boolean.TRUE.equals(c.getRoom().getActive()) &&
          c.getRoom().getCourtType() != null &&
          Boolean.TRUE.equals(c.getRoom().getCourtType().getActive()) &&
          !c
            .getRoom()
            .getCourtType()
            .getName()
            .toUpperCase(Locale.ROOT)
            .contains("DAILY")
      )
      .sorted(Comparator.comparing(Court::getName))
      .map(c ->
        new CourtOption(
          c.getId(),
          c.getName(),
          c.getRoom().getName(),
          c.getRoom().getCourtType().getName(),
          priceOption(
            prices
              .findByCourtTypeIdAndActiveTrue(
                c.getRoom().getCourtType().getId()
              )
              .orElse(null)
          )
        )
      )
      .toList();
    return new Options(products, courtOptions);
  }

  // Sửa trước check-in; sau check-in chỉ gia hạn giờ kết thúc.
  @Transactional
  public NormalBooking edit(Long id, EditRequest request) {
    NormalBooking b = locked(id);
    if (
      request == null ||
      request.courtId() == null ||
      request.bookingDate() == null ||
      request.startTime() == null ||
      request.endTime() == null
    ) throw conflict("Điền đủ sân, ngày và giờ");
    LocalDateTime current = now();
    boolean playing = "CHECKED_IN".equals(b.getStatus());
    if (
      b.getPaidAt() != null || (!playing && !"PENDING".equals(b.getStatus()))
    ) throw conflict(
      "Chỉ sửa lịch đang chờ hoặc gia hạn lịch đang chơi chưa thanh toán"
    );
    if (
      playing &&
      (!request.courtId().equals(b.getCourt().getId()) ||
        !request.bookingDate().equals(b.getBookingDate()) ||
        !request.startTime().equals(b.getStartTime()) ||
        !request.endTime().isAfter(b.getEndTime()) ||
        !current.isBefore(LocalDateTime.of(b.getBookingDate(), b.getEndTime())))
    ) throw conflict("Đang chơi chỉ được gia hạn trước khi hết giờ");
    if (
      !playing &&
      !current.isBefore(
        LocalDateTime.of(request.bookingDate(), request.startTime())
      )
    ) throw conflict("Lịch mới phải bắt đầu trong tương lai");
    if (
      request
        .bookingDate()
        .isAfter(policy.lastBookingDate(current.toLocalDate()))
    ) throw conflict("Ngày đặt vượt giới hạn của hệ thống");
    long minutes = Duration.between(
      request.startTime(),
      request.endTime()
    ).toMinutes();
    if (
      minutes < 60 ||
      request.startTime().getMinute() % 30 != 0 ||
      request.endTime().getMinute() % 30 != 0 ||
      request.startTime().getSecond() != 0 ||
      request.endTime().getSecond() != 0 ||
      request.startTime().getNano() != 0 ||
      request.endTime().getNano() != 0
    ) throw conflict("Chọn giờ :00 hoặc :30, tối thiểu một giờ");
    // Cùng thứ tự khóa sân với API tạo booking; tránh đổi sân đối nhau gây deadlock.
    Set<Long> ids = new TreeSet<>(
      List.of(b.getCourt().getId(), request.courtId())
    );
    Court target = null;
    for (Long courtId : ids) {
      Court c = courts
        .findByIdForBooking(courtId)
        .orElseThrow(() -> conflict("Không tìm thấy sân"));
      if (courtId.equals(request.courtId())) target = c;
    }
    if (
      target == null ||
      !Boolean.TRUE.equals(target.getActive()) ||
      target.getRoom() == null ||
      !Boolean.TRUE.equals(target.getRoom().getActive()) ||
      target.getRoom().getCourtType() == null ||
      !Boolean.TRUE.equals(target.getRoom().getCourtType().getActive())
    ) throw conflict("Sân đang ngừng hoạt động");
    if (
      target
        .getRoom()
        .getCourtType()
        .getName()
        .toUpperCase(Locale.ROOT)
        .contains("DAILY")
    ) throw conflict("Daily Visitor phải đăng ký ca cố định");
    LocalDateTime start = LocalDateTime.of(
      request.bookingDate(),
      request.startTime()
    );
    LocalDateTime end = LocalDateTime.of(
      request.bookingDate(),
      request.endTime()
    );
    if (
      maintenance.existsActiveMaintenanceOverlap(target.getId(), start, end)
    ) throw conflict("Khung giờ có bảo trì");
    for (NormalBooking other : bookings.findByCourtIdAndBookingDateAndStatusNot(
      target.getId(),
      request.bookingDate(),
      "CANCELLED"
    )) {
      if (
        !other.getId().equals(id) &&
        !"NO_SHOW".equals(other.getStatus()) &&
        request.startTime().isBefore(other.getEndTime()) &&
        request.endTime().isAfter(other.getStartTime())
      ) throw conflict("Khung giờ đã có booking khác");
    }
    var sessions = em
      .createQuery(
        "select s from DailyVisitorSession s where s.schedule.court.id = :court and s.sessionDate = :date and s.status <> 'CANCELLED'",
        DailyVisitorSession.class
      )
      .setParameter("court", target.getId())
      .setParameter("date", request.bookingDate())
      .getResultList();
    for (var s : sessions)
      if (
        request.startTime().isBefore(s.getEndTime()) &&
        request.endTime().isAfter(s.getStartTime())
      ) throw conflict("Khung giờ trùng ca Daily Visitor");
    // Booking online vẫn phải tuân thủ giới hạn tài khoản khi đổi ngày.
    if (
      !playing &&
      b.getUser() != null &&
      !request.bookingDate().equals(b.getBookingDate())
    ) throw conflict(
      "Booking online chỉ đổi sân và giờ trong cùng ngày; đổi ngày cần hủy và đặt lại"
    );
    CourtPrice price = prices
      .findByCourtTypeIdAndActiveTrue(target.getRoom().getCourtType().getId())
      .orElseThrow(() -> conflict("Sân chưa có bảng giá"));
    if (playing) {
      long extra = amount(price, b.getEndTime(), request.endTime());
      b.setTotalAmount(Math.addExact(b.getTotalAmount(), extra));
    } else b.setTotalAmount(
      Math.addExact(
        amount(price, request.startTime(), request.endTime()),
        b.getShuttlecockAmount() == null ? 0 : b.getShuttlecockAmount()
      )
    );
    b.setCourt(target);
    b.setBookingDate(request.bookingDate());
    b.setStartTime(request.startTime());
    b.setEndTime(request.endTime());
    return bookings.save(b);
  }

  private long amount(CourtPrice p, LocalTime start, LocalTime end) {
    if (
      !end.isAfter(start) ||
      start.isBefore(p.getOpeningTime()) ||
      end.isAfter(p.getClosingTime())
    ) throw conflict("Khung giờ ngoài giờ mở cửa");
    LocalTime peak = p.getPeakStartTime();
    long normal = start.isBefore(peak)
      ? Duration.between(start, end.isBefore(peak) ? end : peak).toMinutes()
      : 0;
    long high = end.isAfter(peak)
      ? Duration.between(start.isAfter(peak) ? start : peak, end).toMinutes()
      : 0;
    return Math.addExact(
      Math.multiplyExact(p.getNormalPricePerHour(), normal) / 60,
      Math.multiplyExact(p.getPeakPricePerHour(), high) / 60
    );
  }

  @Transactional
  public Object addTubes(Long id, TubeRequest request) {
    if (
      request == null ||
      request.productId() == null ||
      request.quantityTubes() == null ||
      request.quantityTubes() < 1 ||
      request.quantityTubes() > 10000
    ) throw conflict("Chọn loại cầu và số ống hợp lệ");
    NormalBooking b = locked(id);
    if (b.getPaidAt() != null) throw conflict(
      "Booking đã thu tiền: dùng Bán cầu riêng để lập hóa đơn phát sinh"
    );
    if (!"PENDING".equals(b.getStatus())) return bills.addItem(
      id,
      new BookingAddItemRequest(request.productId(), request.quantityTubes(), 0)
    );
    if (
      b.getShuttlecockQuantityPieces() != null &&
      b.getShuttlecockQuantityPieces() > 0
    ) throw conflict("Booking đang giữ cầu lẻ; chỉ thêm ống sau check-in");
    if (Boolean.TRUE.equals(b.getShuttlecockIssued())) throw conflict(
      "Cầu đã giao, không thể sửa phần giữ hàng"
    );
    if (
      b.getShuttlecockProduct() != null &&
      !b.getShuttlecockProduct().getId().equals(request.productId())
    ) throw conflict(
      "Trước check-in chỉ thêm cùng loại cầu đã đặt; loại khác có thể thêm sau check-in"
    );
    Product p = reservations.reserveProduct(
      request.productId(),
      request.quantityTubes()
    );
    long extra = Math.multiplyExact(p.getTubePrice(), request.quantityTubes());
    // Nếu giá hiện tại thay đổi, giữ một giá chụp nhất quán cho phần đặt trước.
    if (
      b.getShuttlecockProduct() != null &&
      !Objects.equals(b.getShuttlecockUnitPrice(), p.getTubePrice())
    ) throw conflict("Giá cầu đã thay đổi, hãy thêm sản phẩm sau check-in");
    b.setShuttlecockProduct(p);
    b.setShuttlecockUnitPrice(p.getTubePrice());
    b.setShuttlecockQuantityTubes(
      Math.addExact(
        b.getShuttlecockQuantityTubes() == null
          ? 0
          : b.getShuttlecockQuantityTubes(),
        request.quantityTubes()
      )
    );
    b.setShuttlecockAmount(
      Math.addExact(
        b.getShuttlecockAmount() == null ? 0 : b.getShuttlecockAmount(),
        extra
      )
    );
    b.setTotalAmount(Math.addExact(b.getTotalAmount(), extra));
    b.setShuttlecockReservationActive(true);
    return bookings.save(b);
  }

  @Transactional
  public SaleReceipt sale(SaleRequest request, Long staffId) {
    if (request == null || request.productId() == null) throw conflict(
      "Chọn sản phẩm"
    );
    int tubes = request.quantityTubes() == null ? 0 : request.quantityTubes();
    int pieces =
      request.quantityPieces() == null ? 0 : request.quantityPieces();
    if (
      tubes < 0 ||
      pieces < 0 ||
      tubes > 10000 ||
      pieces > 10000 ||
      tubes > 0 == pieces > 0
    ) throw conflict(
      "Chọn bán nguyên ống hoặc cầu lẻ, số lượng phải lớn hơn 0"
    );
    if (request.bookingId() != null) locked(request.bookingId());
    InventoryIssue issue;
    if (pieces > 0) {
      var sale = new com.badminton.booking.dto.LoosePieceSaleRequest();
      sale.setProductId(request.productId());
      sale.setQuantityPieces(pieces);
      issue = inventory.sellLoosePieces(sale);
    } else {
      CounterSaleRequest sale = new CounterSaleRequest();
      sale.setProductId(request.productId());
      sale.setQuantityTubes(tubes);
      issue = inventory.sellAtCounter(sale);
    }
    long total = issue.getTotalAmount();
    String method = request.paymentMethod();
    Long received = request.amountReceived();
    if (
      !List.of("CASH", "BANK_TRANSFER").contains(
        method == null ? "" : method
      ) ||
      received == null ||
      received < total ||
      ("BANK_TRANSFER".equals(method) && received != total)
    ) throw conflict(
      "Tiền mặt phải đủ tiền; chuyển khoản phải bằng đúng tổng tiền"
    );
    String customerName =
      request.customerName() == null ? null : request.customerName().trim();
    String customerPhone =
      request.customerPhone() == null
        ? null
        : request.customerPhone().trim().replaceAll("[\\s.-]", "");
    if (
      customerPhone != null && customerPhone.startsWith("+84")
    ) customerPhone = "0" + customerPhone.substring(3);
    if (customerName != null && customerName.length() > 150) throw conflict(
      "Tên người mua tối đa 150 ký tự"
    );
    if (
      customerPhone != null &&
      !customerPhone.isBlank() &&
      !customerPhone.matches("^0[35789]\\d{8}$")
    ) throw conflict("SĐT người mua không hợp lệ");
    CounterSalePayment payment = new CounterSalePayment();
    payment.setCustomerName(
      customerName == null || customerName.isBlank() ? null : customerName
    );
    payment.setCustomerPhone(
      customerPhone == null || customerPhone.isBlank() ? null : customerPhone
    );
    payment.setIssueId(issue.getId());
    payment.setBookingId(request.bookingId());
    payment.setPaymentMethod(method);
    payment.setAmountReceived(received);
    payment.setTotalAmount(total);
    payment.setPaidAt(now());
    payment.setPaidBy(staffId);
    em.persist(payment);
    return saleReceipt(payment, issue);
  }

  private SaleReceipt saleReceipt(CounterSalePayment p, InventoryIssue issue) {
    return new SaleReceipt(
      p.getId(),
      issue.getIssueCode(),
      p.getBookingId(),
      issue.getProduct().getName(),
      issue.getQuantityTubes(),
      issue.getQuantityPieces(),
      issue.getUnitPrice(),
      p.getTotalAmount(),
      p.getPaymentMethod(),
      p.getAmountReceived(),
      p.getAmountReceived() - p.getTotalAmount(),
      p.getPaidAt()
    );
  }

  @Transactional(readOnly = true)
  public List<SaleReceipt> sales(Long bookingId) {
    String query =
      "select p from CounterSalePayment p" +
      (bookingId == null ? "" : " where p.bookingId = :booking") +
      " order by p.paidAt desc";
    var q = em.createQuery(query, CounterSalePayment.class);
    if (bookingId != null) q.setParameter("booking", bookingId);
    return q
      .setMaxResults(100)
      .getResultList()
      .stream()
      .map(p -> saleReceipt(p, em.find(InventoryIssue.class, p.getIssueId())))
      .toList();
  }
}
