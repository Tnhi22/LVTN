package com.badminton.booking.service;

import com.badminton.booking.dto.AdminUserResponse;
import com.badminton.booking.dto.AdminUserUpdateRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Attribute;
import java.util.Locale;
import java.util.Objects;
import java.time.LocalDateTime;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.repository.UserViolationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserViolationRepository violationRepository;
    private final EntityManager entityManager;

    public AdminUserService(
            UserRepository userRepository,
            UserViolationRepository violationRepository, EntityManager entityManager) {

        this.userRepository = userRepository;
        this.violationRepository = violationRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getUsers(
            String keyword,
            String status) {

        String normalizedKeyword = keyword == null
                ? ""
                : keyword.trim().toLowerCase();

        String normalizedStatus = status == null
                ? ""
                : status.trim().toUpperCase();

        return userRepository.findAll()
                .stream()
                .filter(user ->
                        normalizedKeyword.isBlank()
                                || containsIgnoreCase(
                                        user.getFullName(),
                                        normalizedKeyword
                                )
                                || containsIgnoreCase(
                                        user.getEmail(),
                                        normalizedKeyword
                                )
                                || containsIgnoreCase(
                                        user.getPhone(),
                                        normalizedKeyword
                                )
                )
                .filter(user ->
                        normalizedStatus.isBlank()
                                || normalizedStatus.equals(
                                        user.getStatus()
                                )
                )
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long userId) {

        User user = findUser(userId);

        return toResponse(user);
    }

    private User findUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy người dùng"
                        )
                );
    }

    private boolean containsIgnoreCase(
            String value,
            String keyword) {

        return value != null
                && value.toLowerCase().contains(keyword);
    }

    private AdminUserResponse toResponse(User user) {

        long violationCount =
                violationRepository.countByUserId(
                        user.getId()
                );

        return new AdminUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getAuthProvider(),
                user.getEmailVerified(),
                user.getPhoneVerified(),
                user.getRole(),
                user.getStatus(),
                violationCount
        );
    }




            @Transactional
        public AdminUserResponse suspendUser(
                Long userId,
                Long adminId) {

            validateAdmin(adminId);

            if (userId.equals(adminId)) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "ADMIN không thể tự khóa tài khoản của mình"
                );
            }

            User user = manageableUser(userId, adminId);

            if ("SUSPENDED".equals(user.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Tài khoản đã bị khóa"
                );
            }

            user.setStatus("SUSPENDED");

            return toResponse(
                    userRepository.save(user)
            );
        }

        @Transactional
        public AdminUserResponse activateUser(
                Long userId,
                Long adminId) {

            validateAdmin(adminId);

            if (userId.equals(adminId)) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "ADMIN không thể tự thay đổi trạng thái của mình"
                );
            }

            User user = manageableUser(userId, adminId);

            if ("ACTIVE".equals(user.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Tài khoản đang hoạt động"
                );
            }

            user.setStatus("ACTIVE");

            return toResponse(
                    userRepository.save(user)
            );
        }

    private User manageableUser(Long userId, Long adminId) {
        if (userId.equals(adminId)) throw new BusinessException(HttpStatus.CONFLICT, "Không thể thao tác trên tài khoản của chính mình");
        User user = findUser(userId);
        if (!List.of("STAFF", "CUSTOMER").contains(user.getRole()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ quản lý tài khoản STAFF và CUSTOMER ở màn hình này");
        return user;
    }

    @Transactional
    public AdminUserResponse updateUser(Long userId, Long adminId, AdminUserUpdateRequest request) {
        validateAdmin(adminId);
        User user = manageableUser(userId, adminId);
        String name = request.fullName().trim();
        String email = request.email() == null || request.email().isBlank() ? null : request.email().trim().toLowerCase(Locale.ROOT);
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().replaceAll("\\s+", "");
        if (phone != null && phone.startsWith("+84")) phone = "0" + phone.substring(3);
        if (phone != null && !phone.matches("^0[35789]\\d{8}$"))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số điện thoại Việt Nam không hợp lệ");
        if ("PHONE".equals(user.getAuthProvider()) && phone == null)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Tài khoản đăng nhập bằng số điện thoại phải có số điện thoại");
        if ("GOOGLE".equals(user.getAuthProvider()) && !Objects.equals(email, user.getEmail()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email Google được giữ theo tài khoản đăng nhập");
        if (name.isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Tên không được để trống");
        if (email != null) {
            long count = entityManager.createQuery("SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = :email AND u.id <> :id", Long.class)
                    .setParameter("email", email).setParameter("id", userId).getSingleResult();
            if (count > 0) throw new BusinessException(HttpStatus.CONFLICT, "Email đã được sử dụng");
        }
        if (phone != null && userRepository.findByPhone(phone).filter(u -> !u.getId().equals(userId)).isPresent())
            throw new BusinessException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");
        if (!Objects.equals(user.getPhone(), phone)) user.setPhoneVerified(false);
        if (!Objects.equals(user.getEmail(), email)) user.setEmailVerified(false);
        user.setFullName(name); user.setEmail(email); user.setPhone(phone);
        return toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void deleteUser(Long userId, Long adminId) {
        validateAdmin(adminId);
        User user = manageableUser(userId, adminId);
        // Guard all mapped User relationships, including bookings, violations and OTPs.
        // Staff audit IDs are also stored as scalar columns rather than foreign keys.
        List<String> auditIds = List.of("checkedInBy", "cancelledByStaffId", "completedBy", "paidBy", "complaintResolvedBy", "repliedBy", "cancelledBy");
        for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
            for (Attribute<?, ?> attribute : entity.getAttributes()) {
                String expression = null;
                if (attribute.isAssociation() && attribute.getJavaType().equals(User.class)) expression = "e." + attribute.getName() + ".id";
                else if (attribute.getJavaType().equals(Long.class) && auditIds.contains(attribute.getName())) expression = "e." + attribute.getName();
                if (expression != null) {
                    long count = entityManager.createQuery("SELECT COUNT(e) FROM " + entity.getName() + " e WHERE " + expression + " = :id", Long.class)
                            .setParameter("id", userId).getSingleResult();
                    if (count > 0) throw new BusinessException(HttpStatus.CONFLICT,
                            "Tài khoản đã có lịch sử hoặc dữ liệu liên quan. Hãy khóa thay vì xóa để giữ lịch sử.");
                }
            }
        }
        userRepository.delete(user);
        userRepository.flush();
    }

    public record ViolationResponse(Long id, Long bookingId, String violationType, String userStatusAfter, LocalDateTime createdAt) {}

    @Transactional(readOnly = true)
    public List<ViolationResponse> getViolations(Long userId) {
        findUser(userId);
        return violationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(v -> new ViolationResponse(v.getId(), v.getBooking().getId(), v.getViolationType(), v.getUserStatusAfter(), v.getCreatedAt())).toList();
    }

        private void validateAdmin(Long adminId) {

            User admin = findUser(adminId);

            if (!"ADMIN".equals(admin.getRole())) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Chỉ ADMIN được quản lý tài khoản"
                );
            }

            if (!"ACTIVE".equals(admin.getStatus())) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản ADMIN hiện không hoạt động"
                );
            }
        }
}