package com.badminton.booking.controller;

import com.badminton.booking.dto.UpdateProfileRequest;
import com.badminton.booking.dto.UserProfileResponse;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.service.UserProfileService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {

    private final UserProfileService profileService;

    public UserProfileController(
            UserProfileService profileService) {

        this.profileService = profileService;
    }

    @GetMapping
    public UserProfileResponse getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = getUserId(jwt);

        return profileService.getProfile(userId);
    }

    @PatchMapping
    public UserProfileResponse updateMyProfile(
            @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = getUserId(jwt);

        return profileService.updateProfile(
                userId,
                request
        );
    }

    private Long getUserId(Jwt jwt) {

        if (jwt == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập"
            );
        }

        return Long.valueOf(jwt.getSubject());
    }

    @PostMapping(
            value = "/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public UserProfileResponse updateAvatar(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = getUserId(jwt);

        return profileService.updateAvatar(
                userId,
                file
        );
    }

}