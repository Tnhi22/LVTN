package com.badminton.booking.service;

import com.badminton.booking.dto.AuthResponse;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class CustomerAuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final EntityManager em;

    public CustomerAuthService(UserRepository users, PasswordEncoder encoder,
            JwtService jwt, EntityManager em) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.em = em;
    }

    public record LoginRequest(
            @NotBlank @Size(max = 255) String identifier,
            @NotBlank @Size(max = 72) String password) {}

    public record RegisterRequest(
            @NotBlank @Size(max = 255) String fullName,
            @NotBlank @Size(max = 30) String phone,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 8, max = 72) String confirmPassword) {}

    public record RegistrationResult(Long id, String message) {}

    private String normalizePhone(String value) {
        String phone = value.trim().replaceAll("[\\s.-]", "");
        if (phone.startsWith("+84")) phone = "0" + phone.substring(3);
        if (!phone.matches("^0[35789]\\d{8}$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập số điện thoại Việt Nam hợp lệ.");
        }
        return phone;
    }

    private List<User> findEmail(String email) {
        return em.createQuery("select u from User u where lower(u.email) = :email", User.class)
                .setParameter("email", email.toLowerCase(Locale.ROOT))
                .setMaxResults(2).getResultList();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.identifier().trim();
        User user;
        if (identifier.contains("@")) {
            List<User> matches = findEmail(identifier);
            // Không chọn tùy tiện nếu dữ liệu cũ có nhiều email chỉ khác hoa/thường.
            user = matches.size() == 1 ? matches.get(0) : null;
        } else {
            user = users.findByPhone(normalizePhone(identifier)).orElse(null);
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72
                || user == null || user.getPassword() == null
                || !encoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "Email, số điện thoại hoặc mật khẩu không đúng.");
        }
        if ("SUSPENDED".equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa.");
        }
        if (!List.of("ACTIVE", "WARNING").contains(user.getStatus() == null ? "" : user.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Tài khoản hiện không hoạt động.");
        }
        return new AuthResponse(jwt.generateToken(user), "Bearer", user.getId(),
                user.getFullName(), user.getEmail(), user.getPhone(), user.getRole());
    }

    @Transactional
    public RegistrationResult register(RegisterRequest request) {
        String phone = normalizePhone(request.phone());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mật khẩu không được vượt quá 72 byte.");
        }
        if (users.existsByPhone(phone)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Số điện thoại này đã có tài khoản.");
        }
        if (!findEmail(email).isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email này đã có tài khoản.");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        user.setEmail(email);
        user.setPassword(encoder.encode(request.password()));
        user.setAuthProvider("PHONE");
        user.setProviderId(null);
        user.setPhoneVerified(false);
        user.setEmailVerified(false);
        // Đăng ký công khai luôn tạo khách hàng, không lấy quyền từ request.
        user.setRole("CUSTOMER");
        user.setStatus("ACTIVE");
        User saved = users.saveAndFlush(user);
        return new RegistrationResult(saved.getId(), "Đăng ký thành công. Bạn có thể đăng nhập bằng email hoặc số điện thoại.");
    }
}
