package com.badminton.booking.controller;

import com.badminton.booking.dto.AdminUserResponse;
import com.badminton.booking.dto.AdminUserCreateRequest;
import com.badminton.booking.service.AdminUserCreationService;
import com.badminton.booking.dto.AdminUserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.service.AdminUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminUserCreationService creationService;

    public AdminUserController(
            AdminUserService adminUserService, AdminUserCreationService creationService) {

        this.adminUserService = adminUserService;
        this.creationService = creationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse createUser(
            @Valid @RequestBody AdminUserCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return creationService.create(adminId(jwt), request);
    }

    // ADMIN xem, tìm kiếm và lọc danh sách tài khoản
    @GetMapping
    public List<AdminUserResponse> getUsers(
            @RequestParam(required = false)
            String keyword,

            @RequestParam(required = false)
            String status) {

        return adminUserService.getUsers(
                keyword,
                status
        );
    }

    // ADMIN xem chi tiết một tài khoản
    @GetMapping("/{userId}")
    public AdminUserResponse getUser(
            @PathVariable Long userId) {

        return adminUserService.getUser(userId);
    }


    @PatchMapping("/{userId}/suspend")
    public AdminUserResponse suspendUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = adminId(jwt);

        return adminUserService.suspendUser(
                userId,
                adminId
        );
    }

    @PatchMapping("/{userId}/activate")
    public AdminUserResponse activateUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = adminId(jwt);

        return adminUserService.activateUser(
                userId,
                adminId
        );
    }

    private Long adminId(Jwt jwt) {
        if (jwt == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        return Long.valueOf(jwt.getSubject());
    }

    @PutMapping("/{userId}")
    public AdminUserResponse updateUser(@PathVariable Long userId,
            @Valid @RequestBody AdminUserUpdateRequest request, @AuthenticationPrincipal Jwt jwt) {
        return adminUserService.updateUser(userId, adminId(jwt), request);
    }

    @DeleteMapping("/{userId}")
    public java.util.Map<String, String> deleteUser(@PathVariable Long userId, @AuthenticationPrincipal Jwt jwt) {
        adminUserService.deleteUser(userId, adminId(jwt));
        return java.util.Map.of("message", "Đã xóa tài khoản");
    }

    @GetMapping("/{userId}/violations")
    public List<AdminUserService.ViolationResponse> violations(@PathVariable Long userId) {
        return adminUserService.getViolations(userId);
    }
}
