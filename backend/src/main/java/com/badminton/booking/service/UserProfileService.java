package com.badminton.booking.service;

import com.badminton.booking.dto.UpdateProfileRequest;
import com.badminton.booking.dto.UserProfileResponse;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserAvatarService avatarService;

    public UserProfileService(
            UserRepository userRepository,
            UserAvatarService avatarService) {

        this.userRepository = userRepository;
        this.avatarService = avatarService;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {

        User user = findUser(userId);

        return toResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(
            Long userId,
            UpdateProfileRequest request) {

        if (request == null
                || request.getFullName() == null
                || request.getFullName().isBlank()) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Họ tên không được để trống"
            );
        }

        String fullName =
                request.getFullName().trim();

        if (fullName.length() < 2
                || fullName.length() > 100) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Họ tên phải có từ 2 đến 100 ký tự"
            );
        }

        User user = findUser(userId);
        user.setFullName(fullName);

        User savedUser =
                userRepository.save(user);

        return toResponse(savedUser);
    }

    private User findUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy tài khoản"
                        )
                );
    }

    private UserProfileResponse toResponse(
            User user) {

        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getAuthProvider(),
                user.getEmailVerified(),
                user.getPhoneVerified(),
                user.getRole(),
                user.getStatus()
        );
    }
    @Transactional
    public UserProfileResponse updateAvatar(
            Long userId,
            MultipartFile file) {

        User user = findUser(userId);

        String oldAvatarUrl =
                user.getAvatarUrl();

        String newAvatarUrl =
                avatarService.saveAvatar(file);

        user.setAvatarUrl(newAvatarUrl);

        User savedUser =
                userRepository.save(user);

        avatarService.deleteAvatar(oldAvatarUrl);

        return toResponse(savedUser);
    }
}