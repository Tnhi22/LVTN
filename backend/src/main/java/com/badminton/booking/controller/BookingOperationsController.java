package com.badminton.booking.controller;

import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.service.BookingOperationsService;
import com.badminton.booking.service.BookingOperationsService.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Kiểm tra vai trò tại mọi endpoint, kể cả khi SecurityConfig chỉ yêu cầu đăng nhập. */
@RestController
@RequestMapping("/api/booking-operations")
public class BookingOperationsController {

  private final BookingOperationsService operations;
  private final UserRepository users;

  public BookingOperationsController(BookingOperationsService operations, UserRepository users) {
    this.operations = operations;
    this.users = users;
  }

  private Long staff(Jwt jwt) {
    if (jwt == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập");
    User user = users
      .findById(Long.valueOf(jwt.getSubject()))
      .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "Không tìm thấy nhân viên"));
    if (!List.of("ADMIN", "STAFF").contains(user.getRole())) throw new BusinessException(
      HttpStatus.FORBIDDEN,
      "Chỉ Admin hoặc Staff được thực hiện"
    );
    return user.getId();
  }

  @GetMapping("/options")
  public Options options(@AuthenticationPrincipal Jwt jwt) {
    staff(jwt);
    return operations.options();
  }

  @PatchMapping("/{id}")
  public Object edit(
    @PathVariable Long id,
    @RequestBody EditRequest request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    staff(jwt);
    return operations.edit(id, request);
  }

  @PostMapping("/{id}/tubes")
  public Object tubes(
    @PathVariable Long id,
    @RequestBody TubeRequest request,
    @AuthenticationPrincipal Jwt jwt
  ) {
    staff(jwt);
    return operations.addTubes(id, request);
  }

  @PostMapping("/counter-sales")
  public SaleReceipt sale(@RequestBody SaleRequest request, @AuthenticationPrincipal Jwt jwt) {
    return operations.sale(request, staff(jwt));
  }

  @GetMapping("/counter-sales")
  public List<SaleReceipt> sales(
    @RequestParam(required = false) Long bookingId,
    @AuthenticationPrincipal Jwt jwt
  ) {
    staff(jwt);
    return operations.sales(bookingId);
  }
}
