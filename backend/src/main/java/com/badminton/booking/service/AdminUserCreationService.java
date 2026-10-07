package com.badminton.booking.service;

import com.badminton.booking.dto.AdminUserCreateRequest;
import com.badminton.booking.dto.AdminUserResponse;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserCreationService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public AdminUserCreationService(UserRepository users, PasswordEncoder passwordEncoder,
            EntityManager entityManager) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @Transactional
    public AdminUserResponse create(Long adminId, AdminUserCreateRequest request) {
        User admin = users.findById(adminId).orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập"));
        if (!"ADMIN".equals(admin.getRole()) || !"ACTIVE".equals(admin.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ ADMIN đang hoạt động được tạo tài khoản");
        }
        if (request.role() == null || !List.of("STAFF", "CUSTOMER").contains(request.role())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Vai trò phải là STAFF hoặc CUSTOMER");
        }
        String name = request.fullName() == null ? "" : request.fullName().trim();
        String phone = request.phone() == null ? "" : request.phone().replaceAll("\\s+", "");
        if (phone.startsWith("+84")) phone = "0" + phone.substring(3);
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT);
        String password = request.password();
        if (name.isBlank() || name.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Họ tên phải có từ 1 đến 255 ký tự");
        }
        if (!phone.matches("^0[35789]\\d{8}$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số điện thoại Việt Nam không hợp lệ");
        }
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte");
        }
        if (users.existsByPhone(phone)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");
        }
        if (email != null) {
            long count = entityManager.createQuery(
                    "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = :email", Long.class)
                    .setParameter("email", email).getSingleResult();
            if (count > 0) throw new BusinessException(HttpStatus.CONFLICT, "Email đã được sử dụng");
        }
        User user = new User();
        user.setFullName(name);
        user.setPhone(phone);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setAuthProvider("PHONE");
        user.setProviderId(null);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setRole(request.role());
        user.setStatus("ACTIVE");
        User saved = users.saveAndFlush(user);
        return new AdminUserResponse(saved.getId(), saved.getFullName(), saved.getEmail(),
                saved.getPhone(), saved.getAvatarUrl(), saved.getAuthProvider(),
                saved.getEmailVerified(), saved.getPhoneVerified(), saved.getRole(),
                saved.getStatus(), 0L);
    }
}
