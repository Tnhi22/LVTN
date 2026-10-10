package com.badminton.booking.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Thanh toán cho phiếu bán cầu riêng; không thay đổi tổng tiền booking. */
@Entity
@Table(
  name = "counter_sale_payments",
  uniqueConstraints = @UniqueConstraint(columnNames = "issue_id")
)
public class CounterSalePayment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "issue_id", nullable = false)
  private Long issueId;

  private Long bookingId;

  @Column(name = "customer_name", length = 150)
  private String customerName;

  @Column(name = "customer_phone", length = 30)
  private String customerPhone;

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String value) {
    customerName = value;
  }

  public String getCustomerPhone() {
    return customerPhone;
  }

  public void setCustomerPhone(String value) {
    customerPhone = value;
  }

  @Column(nullable = false)
  private String paymentMethod;

  @Column(nullable = false)
  private Long amountReceived;

  @Column(nullable = false)
  private Long totalAmount;

  @Column(nullable = false)
  private LocalDateTime paidAt;

  @Column(nullable = false)
  private Long paidBy;

  public Long getId() {
    return id;
  }

  public Long getIssueId() {
    return issueId;
  }

  public void setIssueId(Long value) {
    issueId = value;
  }

  public Long getBookingId() {
    return bookingId;
  }

  public void setBookingId(Long value) {
    bookingId = value;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(String value) {
    paymentMethod = value;
  }

  public Long getAmountReceived() {
    return amountReceived;
  }

  public void setAmountReceived(Long value) {
    amountReceived = value;
  }

  public Long getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(Long value) {
    totalAmount = value;
  }

  public LocalDateTime getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(LocalDateTime value) {
    paidAt = value;
  }

  public Long getPaidBy() {
    return paidBy;
  }

  public void setPaidBy(Long value) {
    paidBy = value;
  }
}
