package com.badminton.booking.controller;

import com.badminton.booking.dto.AdminUserResponse;
import com.badminton.booking.service.AdminUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(
            AdminUserService adminUserService) {

        this.adminUserService = adminUserService;
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

        Long adminId = Long.valueOf(jwt.getSubject());

        return adminUserService.suspendUser(
                userId,
                adminId
        );
    }

    @PatchMapping("/{userId}/activate")
    public AdminUserResponse activateUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = Long.valueOf(jwt.getSubject());

        return adminUserService.activateUser(
                userId,
                adminId
        );
    }
}