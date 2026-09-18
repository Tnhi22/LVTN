package com.badminton.booking.service;

import com.badminton.booking.dto.AuthResponse;
import com.badminton.booking.dto.PhoneLoginRequest;
import com.badminton.booking.dto.PhoneRegisterRequest;
import com.badminton.booking.dto.PhoneRegisterResponse;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // =====================================================
    // ĐĂNG KÝ BẰNG SỐ ĐIỆN THOẠI
    // =====================================================
    @Transactional
    public PhoneRegisterResponse registerByPhone(
            PhoneRegisterRequest request) {

        if (request == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Dữ liệu đăng ký không được để trống"
            );
        }

        if (request.getFullName() == null
                || request.getFullName().trim().isEmpty()) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Họ tên không được để trống"
            );
        }

        if (request.getPassword() == null
                || request.getPassword().isBlank()) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Mật khẩu không được để trống"
            );
        }

        String fullName = request.getFullName().trim();
        String phone = normalizePhone(request.getPhone());

        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Số điện thoại này đã có tài khoản"
            );
        }

        User user = new User();

        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail(null);

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setAuthProvider("PHONE");
        user.setProviderId(null);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setRole("CUSTOMER");
        user.setStatus("ACTIVE");

        User savedUser = userRepository.save(user);

        return new PhoneRegisterResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getPhone(),
                savedUser.getRole(),
                savedUser.getStatus(),
                savedUser.getPhoneVerified(),
                "Đăng ký thành công"
        );
    }

    // =====================================================
    // ĐĂNG NHẬP BẰNG SỐ ĐIỆN THOẠI
    // =====================================================
    public AuthResponse loginByPhone(
            PhoneLoginRequest request) {

        if (request == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Dữ liệu đăng nhập không được để trống"
            );
        }

        String phone = normalizePhone(
                request.getPhone()
        );

        if (request.getPassword() == null
                || request.getPassword().isBlank()) {

            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Số điện thoại hoặc mật khẩu không đúng"
            );
        }

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.UNAUTHORIZED,
                                "Số điện thoại hoặc mật khẩu không đúng"
                        )
                );

        if (user.getPassword() == null
                || !passwordEncoder.matches(
                        request.getPassword(),
                        user.getPassword()
                )) {

            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Số điện thoại hoặc mật khẩu không đúng"
            );
        }

        // WARNING vẫn được đăng nhập,
        // nhưng sẽ bị chặn đặt sân trong thời hạn cảnh báo.
        if ("SUSPENDED".equals(user.getStatus())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản đã bị khóa"
            );
        }

        if (!"ACTIVE".equals(user.getStatus())
                && !"WARNING".equals(user.getStatus())) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản hiện không hoạt động"
            );
        }

        String accessToken =
                jwtService.generateToken(user);

        return new AuthResponse(
                accessToken,
                "Bearer",
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole()
        );
    }

    // =====================================================
    // CHUẨN HÓA SỐ ĐIỆN THOẠI
    // =====================================================
    private String normalizePhone(
            String rawPhone) {

        if (rawPhone == null
                || rawPhone.trim().isEmpty()) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Số điện thoại không được để trống"
            );
        }

        String phone = rawPhone
                .trim()
                .replaceAll("[\\s.-]", "");

        if (phone.startsWith("+84")) {
            phone = "0" + phone.substring(3);
        }

        if (!phone.matches("^0[35789]\\d{8}$")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Số điện thoại không đúng định dạng Việt Nam"
            );
        }

        return phone;
    }
}