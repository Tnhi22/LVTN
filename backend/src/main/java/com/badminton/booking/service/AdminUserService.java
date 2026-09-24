package com.badminton.booking.service;

import com.badminton.booking.dto.AdminUserResponse;
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

    public AdminUserService(
            UserRepository userRepository,
            UserViolationRepository violationRepository) {

        this.userRepository = userRepository;
        this.violationRepository = violationRepository;
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

            User user = findUser(userId);

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

            User user = findUser(userId);

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