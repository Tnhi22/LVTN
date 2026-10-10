package com.badminton.booking.controller;

import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.service.CounterBookingVerificationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/counter-booking-verification")
public class CounterBookingVerificationController {

  private final CounterBookingVerificationService service;
  private final UserRepository users;

  public CounterBookingVerificationController(
    CounterBookingVerificationService service,
    UserRepository users
  ) {
    this.service = service;
    this.users = users;
  }

  private Long staff(Jwt jwt) {
    if (jwt == null) throw new BusinessException(
      HttpStatus.UNAUTHORIZED,
      "Cần đăng nhập"
    );
    User user = users
      .findById(Long.valueOf(jwt.getSubject()))
      .orElseThrow(() ->
        new BusinessException(HttpStatus.FORBIDDEN, "Không tìm thấy nhân viên")
      );
    if (
      !List.of("STAFF", "ADMIN").contains(user.getRole())
    ) throw new BusinessException(
      HttpStatus.FORBIDDEN,
      "Chỉ Staff/Admin được xác minh khách tại quầy"
    );
    return user.getId();
  }

  public record Request(String phone, String otp) {}

  @PostMapping("/request")
  public Object request(
    @RequestBody Request request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    return service.request(request.phone(), staff(jwt));
  }

  @PostMapping("/verify")
  public Object verify(
    @RequestBody Request request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    return service.verify(request.phone(), request.otp(), staff(jwt));
  }
}
