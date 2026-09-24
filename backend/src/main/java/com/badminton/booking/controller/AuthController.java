package com.badminton.booking.controller;

import com.badminton.booking.dto.AuthResponse;
import com.badminton.booking.dto.ChangePasswordRequest;
import com.badminton.booking.dto.GoogleLoginRequest;
import com.badminton.booking.dto.MessageResponse;
import com.badminton.booking.dto.PhoneLoginRequest;
import com.badminton.booking.dto.PhoneRegisterRequest;
import com.badminton.booking.dto.PhoneRegisterResponse;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/phone")
    public PhoneRegisterResponse registerByPhone(
            @Valid @RequestBody PhoneRegisterRequest request) {

        return authService.registerByPhone(request);
    }

    @PostMapping("/login/phone")
    public AuthResponse loginByPhone(
            @Valid @RequestBody PhoneLoginRequest request) {

        return authService.loginByPhone(request);
    }

    @PostMapping("/login/google")
    public AuthResponse loginByGoogle(
            @Valid @RequestBody GoogleLoginRequest request) {

        return authService.loginByGoogle(request);
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập"
            );
        }

        Long userId = Long.valueOf(jwt.getSubject());

        authService.changePassword(
                userId,
                request
        );

        return new MessageResponse(
                "Đổi mật khẩu thành công"
        );
    }
}