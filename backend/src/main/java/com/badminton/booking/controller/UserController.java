package com.badminton.booking.controller;

import com.badminton.booking.dto.LoginRequest;
import com.badminton.booking.dto.LoginResponse;
import com.badminton.booking.dto.RegisterRequest;
import com.badminton.booking.entity.User;
import com.badminton.booking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public User register(@RequestBody RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã tồn tại");
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("Số điện thoại đã tồn tại");
        }

        User user = new User();

        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());

        // Mã hóa mật khẩu trước khi lưu
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Người đăng ký luôn là CUSTOMER
        user.setRole("CUSTOMER");
        user.setStatus("ACTIVE");

        return userRepository.save(user);
    }

    @PostMapping("/login")
public LoginResponse login(@RequestBody LoginRequest request) {

    User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("Email hoặc mật khẩu không đúng"));

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        throw new RuntimeException("Email hoặc mật khẩu không đúng");
    }

    if (!"ACTIVE".equals(user.getStatus())) {
        throw new RuntimeException("Tài khoản không hoạt động");
    }

    return new LoginResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getPhone(),
            user.getRole()
    );
}
}