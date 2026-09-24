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
import com.badminton.booking.dto.GoogleLoginRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import java.nio.charset.StandardCharsets;
import com.badminton.booking.dto.ChangePasswordRequest;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifierService
        googleTokenVerifierService;

public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        GoogleTokenVerifierService googleTokenVerifierService) {

    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.googleTokenVerifierService =
            googleTokenVerifierService;
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

        validatePassword(request.getPassword());

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
        // KIỂM TRA MẬT KHẨU
        // =====================================================
        private void validatePassword(String password) {

        if (password == null || password.isBlank()) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Mật khẩu không được để trống"
                );
        }

        if (password.length() < 8) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Mật khẩu phải có ít nhất 8 ký tự"
                );
        }

        int passwordBytes = password
                .getBytes(StandardCharsets.UTF_8)
                .length;

        if (passwordBytes > 72) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Mật khẩu không được vượt quá 72 byte"
                );
        }
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

        // =====================================================
        // ĐĂNG NHẬP BẰNG GOOGLE
        // =====================================================
        @Transactional
        public AuthResponse loginByGoogle(
                GoogleLoginRequest request) {

        if (request == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Dữ liệu đăng nhập Google không được để trống"
                );
        }

        GoogleIdToken.Payload payload =
                googleTokenVerifierService.verify(
                        request.getIdToken()
                );

        String providerId = payload.getSubject();

        String email = payload.getEmail();

        if (email == null || email.isBlank()) {
                throw new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "Tài khoản Google không cung cấp email"
                );
        }

        email = email.trim()
                .toLowerCase(Locale.ROOT);

        String fullName =
                (String) payload.get("name");

        if (fullName == null || fullName.isBlank()) {
                fullName = email.substring(
                        0,
                        email.indexOf("@")
                );
        }

        User user = userRepository
                .findByProviderId(providerId)
                .orElse(null);

        if (user == null) {
                user = userRepository
                        .findByEmail(email)
                        .orElse(null);
        }

        if (user == null) {
                user = new User();

                user.setFullName(fullName.trim());
                user.setEmail(email);
                user.setPhone(null);
                user.setPassword(null);
                user.setAuthProvider("GOOGLE");
                user.setProviderId(providerId);
                user.setEmailVerified(true);
                user.setPhoneVerified(false);
                user.setRole("CUSTOMER");
                user.setStatus("ACTIVE");

                user = userRepository.save(user);

        } else {
                String currentProviderId =
                        user.getProviderId();

                if (currentProviderId != null
                        && !currentProviderId.equals(
                                providerId
                        )) {

                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Email đã liên kết với một tài khoản khác"
                );
                }

                user.setProviderId(providerId);
                user.setEmailVerified(true);

                if ("PHONE".equals(user.getAuthProvider())) {
                user.setAuthProvider("PHONE_GOOGLE");
                } else {
                user.setAuthProvider("GOOGLE");
                }

                user = userRepository.save(user);
        }

        if ("SUSPENDED".equals(user.getStatus())) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản đã bị khóa"
                );
        }

        if (!"ACTIVE".equals(user.getStatus())
                && !"WARNING".equals(
                        user.getStatus()
                )) {

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



        @Transactional
        public void changePassword(
                Long userId,
                ChangePasswordRequest request) {

        if (request == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Dữ liệu đổi mật khẩu không hợp lệ"
                );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy tài khoản"
                        )
                );

        // Tài khoản Google chưa có mật khẩu
        if (user.getPassword() == null) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Tài khoản Google chưa thiết lập mật khẩu"
                );
        }

        if (request.getCurrentPassword() == null
                || !passwordEncoder.matches(
                        request.getCurrentPassword(),
                        user.getPassword()
                )) {

                throw new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "Mật khẩu hiện tại không đúng"
                );
        }

        validatePassword(request.getNewPassword());

        if (!request.getNewPassword()
                .equals(request.getConfirmPassword())) {

                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Xác nhận mật khẩu mới không khớp"
                );
        }

        if (passwordEncoder.matches(
                request.getNewPassword(),
                user.getPassword()
        )) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Mật khẩu mới phải khác mật khẩu hiện tại"
                );
        }

        user.setPassword(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );

        userRepository.save(user);
        }
}