package com.badminton.booking.service;

import com.badminton.booking.exception.BusinessException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAvatarService {

    private static final long MAX_SIZE =
            5L * 1024 * 1024;

    private static final Map<String, String> ALLOWED_TYPES =
            Map.of(
                    "image/jpeg", ".jpg",
                    "image/png", ".png",
                    "image/webp", ".webp"
            );

    private final Path uploadDirectory;

    public UserAvatarService() {

        this.uploadDirectory = Paths.get(
                "uploads",
                "avatars"
        ).toAbsolutePath().normalize();

        try {
            Files.createDirectories(uploadDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể tạo thư mục lưu ảnh đại diện",
                    exception
            );
        }
    }

    public String saveAvatar(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn ảnh đại diện"
            );
        }

        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Ảnh đại diện không được vượt quá 5 MB"
            );
        }

        String extension =
                ALLOWED_TYPES.get(file.getContentType());

        if (extension == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Ảnh đại diện chỉ hỗ trợ JPG, PNG hoặc WEBP"
            );
        }

        String storedFilename =
                UUID.randomUUID() + extension;

        Path targetPath =
                uploadDirectory.resolve(storedFilename)
                        .normalize();

        if (!targetPath.startsWith(uploadDirectory)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Tên file ảnh không hợp lệ"
            );
        }

        try {
            Files.copy(
                    file.getInputStream(),
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException exception) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể lưu ảnh đại diện"
            );
        }

        return "/uploads/avatars/" + storedFilename;
    }

    public void deleteAvatar(String avatarUrl) {

        if (avatarUrl == null
                || !avatarUrl.startsWith(
                        "/uploads/avatars/")) {
            return;
        }

        String filename = avatarUrl.substring(
                "/uploads/avatars/".length()
        );

        Path targetPath =
                uploadDirectory.resolve(filename)
                        .normalize();

        if (!targetPath.startsWith(uploadDirectory)) {
            return;
        }

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException ignored) {
            // Không làm thất bại cập nhật hồ sơ
            // nếu ảnh cũ không thể xóa.
        }
    }
}