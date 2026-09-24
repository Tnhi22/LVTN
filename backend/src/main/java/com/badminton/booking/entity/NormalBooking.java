package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "normal_bookings")
public class NormalBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "visitor_id")
    private Visitor visitor;

    @ManyToOne
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /*
     * Tổng tiền của booking:
     * tiền sân + tiền ống cầu đặt kèm.
     */
    @Column(name = "total_amount")
    private Long totalAmount;

    // =====================================================
    // THÔNG TIN ỐNG CẦU ĐẶT KÈM
    // =====================================================

    /*
     * Sản phẩm cầu mà khách chọn.
     * Null nếu booking không mua cầu.
     */
    @ManyToOne
    @JoinColumn(name = "shuttlecock_product_id")
    private Product shuttlecockProduct;

    /*
     * Số ống cầu khách đặt.
     * Booking cũ có thể null.
     */
    @Column(name = "shuttlecock_quantity_tubes")
    private Integer shuttlecockQuantityTubes = 0;

    /*
     * Giá một ống tại thời điểm đặt.
     * Lưu lại để sau này sản phẩm đổi giá
     * vẫn không làm thay đổi booking cũ.
     */
    @Column(name = "shuttlecock_unit_price")
    private Long shuttlecockUnitPrice;

    /*
     * Tổng tiền cầu:
     * số ống × giá một ống.
     */
    @Column(name = "shuttlecock_amount")
    private Long shuttlecockAmount = 0L;

    /*
     * true:
     * số lượng đang được giữ cho booking.
     *
     * false:
     * không giữ hàng, đã check-in,
     * đã hủy hoặc đã NO_SHOW.
     */
    @Column(name = "shuttlecock_reservation_active")
    private Boolean shuttlecockReservationActive = false;

    /*
     * true:
     * đã xuất kho FIFO khi check-in.
     *
     * Dùng để ngăn việc gọi check-in nhiều lần
     * làm trừ kho nhiều lần.
     */
    @Column(name = "shuttlecock_issued")
    private Boolean shuttlecockIssued = false;

    // =====================================================
    // CHECK-IN VÀ HỦY BOOKING
    // =====================================================

    private LocalDateTime checkedInAt;

    private Long checkedInBy;

    @Column(name = "cancelled_by_staff_id")
    private Long cancelledByStaffId;

    @Column(name = "cancelled_by_staff_name")
    private String cancelledByStaffName;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // =====================================================
    // KHIẾU NẠI
    // =====================================================

    private String complaintStatus = "NONE";
    private String complaintReason;
    private LocalDateTime complainedAt;
    private LocalDateTime complaintResolvedAt;
    private Long complaintResolvedBy;

    public NormalBooking() {
    }

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = "PENDING";
        }

        if (complaintStatus == null) {
            complaintStatus = "NONE";
        }

        if (shuttlecockQuantityTubes == null) {
            shuttlecockQuantityTubes = 0;
        }

        if (shuttlecockAmount == null) {
            shuttlecockAmount = 0L;
        }

        if (shuttlecockReservationActive == null) {
            shuttlecockReservationActive = false;
        }

        if (shuttlecockIssued == null) {
            shuttlecockIssued = false;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Visitor getVisitor() {
        return visitor;
    }

    public void setVisitor(Visitor visitor) {
        this.visitor = visitor;
    }

    public Court getCourt() {
        return court;
    }

    public void setCourt(Court court) {
        this.court = court;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Product getShuttlecockProduct() {
        return shuttlecockProduct;
    }

    public void setShuttlecockProduct(
            Product shuttlecockProduct) {

        this.shuttlecockProduct = shuttlecockProduct;
    }

    public Integer getShuttlecockQuantityTubes() {
        return shuttlecockQuantityTubes;
    }

    public void setShuttlecockQuantityTubes(
            Integer shuttlecockQuantityTubes) {

        this.shuttlecockQuantityTubes =
                shuttlecockQuantityTubes;
    }

    public Long getShuttlecockUnitPrice() {
        return shuttlecockUnitPrice;
    }

    public void setShuttlecockUnitPrice(
            Long shuttlecockUnitPrice) {

        this.shuttlecockUnitPrice =
                shuttlecockUnitPrice;
    }

    public Long getShuttlecockAmount() {
        return shuttlecockAmount;
    }

    public void setShuttlecockAmount(
            Long shuttlecockAmount) {

        this.shuttlecockAmount = shuttlecockAmount;
    }

    public Boolean getShuttlecockReservationActive() {
        return shuttlecockReservationActive;
    }

    public void setShuttlecockReservationActive(
            Boolean shuttlecockReservationActive) {

        this.shuttlecockReservationActive =
                shuttlecockReservationActive;
    }

    public Boolean getShuttlecockIssued() {
        return shuttlecockIssued;
    }

    public void setShuttlecockIssued(
            Boolean shuttlecockIssued) {

        this.shuttlecockIssued = shuttlecockIssued;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(
            LocalDateTime checkedInAt) {

        this.checkedInAt = checkedInAt;
    }

    public Long getCheckedInBy() {
        return checkedInBy;
    }

    public void setCheckedInBy(Long checkedInBy) {
        this.checkedInBy = checkedInBy;
    }

    public Long getCancelledByStaffId() {
        return cancelledByStaffId;
    }

    public void setCancelledByStaffId(
            Long cancelledByStaffId) {

        this.cancelledByStaffId =
                cancelledByStaffId;
    }

    public String getCancelledByStaffName() {
        return cancelledByStaffName;
    }

    public void setCancelledByStaffName(
            String cancelledByStaffName) {

        this.cancelledByStaffName =
                cancelledByStaffName;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(
            LocalDateTime cancelledAt) {

        this.cancelledAt = cancelledAt;
    }

    public String getComplaintStatus() {
        return complaintStatus;
    }

    public void setComplaintStatus(
            String complaintStatus) {

        this.complaintStatus = complaintStatus;
    }

    public String getComplaintReason() {
        return complaintReason;
    }

    public void setComplaintReason(
            String complaintReason) {

        this.complaintReason = complaintReason;
    }

    public LocalDateTime getComplainedAt() {
        return complainedAt;
    }

    public void setComplainedAt(
            LocalDateTime complainedAt) {

        this.complainedAt = complainedAt;
    }

    public LocalDateTime getComplaintResolvedAt() {
        return complaintResolvedAt;
    }

    public void setComplaintResolvedAt(
            LocalDateTime complaintResolvedAt) {

        this.complaintResolvedAt =
                complaintResolvedAt;
    }

    public Long getComplaintResolvedBy() {
        return complaintResolvedBy;
    }

    public void setComplaintResolvedBy(
            Long complaintResolvedBy) {

        this.complaintResolvedBy =
                complaintResolvedBy;
    }
}