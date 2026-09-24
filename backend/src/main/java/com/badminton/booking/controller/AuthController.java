package com.badminton.booking.controller;

import com.badminton.booking.dto.AuthResponse;
import com.badminton.booking.dto.ChangePasswordRequest;
import com.badminton.booking.dto.ForgotPasswordRequest;
import com.badminton.booking.dto.GoogleLoginRequest;
import com.badminton.booking.dto.MessageResponse;
import com.badminton.booking.dto.PhoneLoginRequest;
import com.badminton.booking.dto.PhoneRegisterRequest;
import com.badminton.booking.dto.PhoneRegisterResponse;
import com.badminton.booking.dto.ResetPasswordRequest;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.service.AuthService;
import com.badminton.booking.dto.PhoneVerificationRequest;
import com.badminton.booking.dto.VerifyPhoneRequest;
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

    // Đăng ký bằng số điện thoại
    @PostMapping("/register/phone")
    public PhoneRegisterResponse registerByPhone(
            @Valid @RequestBody PhoneRegisterRequest request) {

        return authService.registerByPhone(request);
    }

    // Đăng nhập bằng số điện thoại
    @PostMapping("/login/phone")
    public AuthResponse loginByPhone(
            @Valid @RequestBody PhoneLoginRequest request) {

        return authService.loginByPhone(request);
    }

    // Đăng nhập bằng Google
    @PostMapping("/login/google")
    public AuthResponse loginByGoogle(
            @Valid @RequestBody GoogleLoginRequest request) {

        return authService.loginByGoogle(request);
    }

    // Người đang đăng nhập đổi mật khẩu
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

    // Yêu cầu gửi OTP quên mật khẩu
    @PostMapping("/forgot-password/request-otp")
    public MessageResponse requestPasswordResetOtp(
            @RequestBody ForgotPasswordRequest request) {

        authService.requestPasswordResetOtp(request);

        return new MessageResponse(
                "Nếu số điện thoại tồn tại, mã OTP đã được gửi"
        );
    }

    // Xác minh OTP và đặt mật khẩu mới
    @PostMapping("/forgot-password/reset")
    public MessageResponse resetPassword(
            @RequestBody ResetPasswordRequest request) {

        authService.resetPassword(request);

        return new MessageResponse(
                "Đặt lại mật khẩu thành công"
        );
    }

    // Gửi OTP để liên kết/xác minh số điện thoại
@PostMapping("/phone/request-verification")
public MessageResponse requestPhoneVerification(
        @RequestBody PhoneVerificationRequest request,
        @AuthenticationPrincipal Jwt jwt) {

    if (jwt == null) {
        throw new BusinessException(
                HttpStatus.UNAUTHORIZED,
                "Vui lòng đăng nhập"
        );
    }

    Long userId = Long.valueOf(jwt.getSubject());

    authService.requestPhoneVerification(
            userId,
            request
    );

    return new MessageResponse(
            "Mã OTP xác minh số điện thoại đã được gửi"
    );
}

    // Xác minh OTP và lưu số điện thoại vào user
    @PostMapping("/phone/verify")
    public MessageResponse verifyPhone(
            @RequestBody VerifyPhoneRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập"
            );
        }

        Long userId = Long.valueOf(jwt.getSubject());

        authService.verifyPhone(
                userId,
                request
        );

        return new MessageResponse(
                "Xác minh số điện thoại thành công"
        );
    }
}