package com.badminton.booking.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "counter_booking_otps")
public class CounterBookingOtp {

  @Id
  @Column(length = 10)
  public String phone;

  @Column(nullable = false)
  public Long staffId;

  @Column(nullable = false)
  public String codeHash;

  @Column(nullable = false)
  public LocalDateTime createdAt;

  @Column(nullable = false)
  public LocalDateTime expiresAt;

  @Column(nullable = false)
  public int attempts;

  public String tokenHash;
  public LocalDateTime verifiedUntil;

  @Column(nullable = false)
  public boolean consumed;

  public CounterBookingOtp() {}
}
