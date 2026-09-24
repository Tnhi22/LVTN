package com.badminton.booking.controller;

import com.badminton.booking.dto.AuthResponse;
import com.badminton.booking.dto.PhoneLoginRequest;
import com.badminton.booking.dto.PhoneRegisterRequest;
import com.badminton.booking.dto.PhoneRegisterResponse;
import com.badminton.booking.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.badminton.booking.dto.GoogleLoginRequest;

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
            @Valid @RequestBody
            GoogleLoginRequest request) {

        return authService.loginByGoogle(request);
    }
}