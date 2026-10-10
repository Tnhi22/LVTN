package com.badminton.booking.service;

import com.badminton.booking.entity.*;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.*;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CounterBookingVerificationService {

  private final EntityManager em;
  private final VisitorRepository visitors;
  private final UserRepository users;
  private final UserViolationRepository violations;
  private final PasswordEncoder encoder;
  private final SecureRandom random = new SecureRandom();

  public CounterBookingVerificationService(
    EntityManager em,
    VisitorRepository visitors,
    UserRepository users,
    UserViolationRepository violations,
    PasswordEncoder encoder
  ) {
    this.em = em;
    this.visitors = visitors;
    this.users = users;
    this.violations = violations;
    this.encoder = encoder;
  }

  private LocalDateTime now() {
    return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
  }

  private BusinessException reject(String message) {
    return new BusinessException(HttpStatus.CONFLICT, message);
  }

  public String normalize(String phone) {
    String value =
      phone == null
        ? ""
        : phone.trim().replaceAll("[\\s.-]", "").replaceFirst("^\\+84", "0");
    if (!value.matches("^0[35789]\\d{8}$")) throw reject(
      "Số điện thoại Việt Nam không hợp lệ"
    );
    return value;
  }

  private void lock(String phone) {
    em.createNativeQuery(
      "select 1 from pg_advisory_xact_lock(hashtextextended(:phone, 0))"
    )
      .setParameter("phone", "counter-otp:" + phone)
      .getSingleResult();
  }

  public void requireEligible(String phone) {
    visitors.findByPhone(phone).ifPresent(visitor -> {
      if (!"ACTIVE".equals(visitor.getStatus())) throw reject(
        "Số điện thoại khách tại quầy đang bị chặn do vi phạm"
      );
    });
    users.findByPhone(phone).ifPresent(user -> {
      if (
        "SUSPENDED".equals(user.getStatus()) ||
        "BLOCKED".equals(user.getStatus())
      ) throw reject(
        "Số điện thoại có tài khoản bị khóa do vi phạm; không được đặt qua quầy"
      );
      if ("WARNING".equals(user.getStatus())) {
        var history = violations.findWarningHistory(user.getId(), "WARNING");
        if (
          history.isEmpty() ||
          now().isBefore(history.get(0).getCreatedAt().plusDays(2))
        ) throw reject(
          "Số điện thoại đang trong thời gian cảnh báo, chưa được đặt sân"
        );
      }
    });
  }

  public record OtpResult(String message, String verificationToken) {}

  @Transactional
  public OtpResult request(String rawPhone, Long staffId) {
    String phone = normalize(rawPhone);
    lock(phone);
    requireEligible(phone);
    var row = em.find(CounterBookingOtp.class, phone);
    LocalDateTime now = now();
    if (
      row != null && now.isBefore(row.createdAt.plusSeconds(60))
    ) throw reject("Vui lòng chờ 60 giây trước khi gửi lại OTP");
    String code = String.format("%06d", random.nextInt(1000000));
    boolean newOtp = row == null;
    if (newOtp) {
      row = new CounterBookingOtp();
      row.phone = phone;
    }
    row.staffId = staffId;
    row.codeHash = encoder.encode(code);
    row.createdAt = now;
    row.expiresAt = now.plusMinutes(5);
    row.attempts = 0;
    row.tokenHash = null;
    row.verifiedUntil = null;
    row.consumed = false;
    if (newOtp) em.persist(row);
    // OTP mô phỏng phục vụ demo luận văn, chỉ in sau khi giao dịch lưu thành công.
    final String terminalPhone = phone;
    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
      new org.springframework.transaction.support.TransactionSynchronization() {
        @Override
        public void afterCommit() {
          System.out.println(
            "[COUNTER BOOKING OTP - DEMO] Phone: " +
              terminalPhone +
              " | OTP: " +
              code +
              " | Expires in 5 minutes"
          );
        }
      }
    );
    return new OtpResult(
      "OTP mô phỏng đã được tạo. Xem terminal backend, nhập mã và bấm Xác minh OTP.",
      null
    );
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public OtpResult verify(String rawPhone, String code, Long staffId) {
    String phone = normalize(rawPhone);
    lock(phone);
    requireEligible(phone);
    var row = em.find(CounterBookingOtp.class, phone);
    if (
      row == null ||
      !staffId.equals(row.staffId) ||
      row.consumed ||
      !row.expiresAt.isAfter(now()) ||
      row.attempts >= 5
    ) throw reject("OTP hết hạn hoặc không còn hiệu lực. Yêu cầu mã mới");
    if (
      code == null ||
      !code.matches("\\d{6}") ||
      !encoder.matches(code, row.codeHash)
    ) {
      row.attempts++;
      throw reject("OTP không đúng (tối đa 5 lần thử)");
    }
    String token = UUID.randomUUID().toString();
    row.tokenHash = encoder.encode(token);
    row.verifiedUntil = now().plusMinutes(10);
    return new OtpResult("Đã xác minh số điện thoại", token);
  }

  @Transactional
  public void consume(String rawPhone, String token, Long staffId) {
    String phone = normalize(rawPhone);
    lock(phone);
    requireEligible(phone);
    var row = em.find(CounterBookingOtp.class, phone);
    if (
      row == null ||
      row.consumed ||
      !staffId.equals(row.staffId) ||
      row.verifiedUntil == null ||
      !row.verifiedUntil.isAfter(now()) ||
      token == null ||
      row.tokenHash == null ||
      !encoder.matches(token, row.tokenHash)
    ) throw reject("Phải xác minh OTP của số điện thoại này trước khi đặt sân");
    row.consumed = true;
  }
}
