package com.badminton.booking.controller;

import com.badminton.booking.dto.AuthResponse;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.service.CustomerAuthService;
import com.badminton.booking.service.CustomerAuthService.LoginRequest;
import com.badminton.booking.service.CustomerAuthService.RegisterRequest;
import com.badminton.booking.service.CustomerAuthService.RegistrationResult;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class CustomerAuthController {
    private final CustomerAuthService auth;

    public CustomerAuthController(CustomerAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResult register(@Valid @RequestBody RegisterRequest request) {
        try {
            return auth.register(request);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Email hoặc số điện thoại đã được sử dụng. Vui lòng kiểm tra lại.");
        }
    }
}
