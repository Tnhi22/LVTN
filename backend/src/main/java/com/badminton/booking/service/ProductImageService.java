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
import java.util.Set;
import java.util.UUID;

@Service
public class ProductImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private final Path uploadDirectory;

    public ProductImageService() {
        this.uploadDirectory = Paths.get(
                "uploads",
                "products"
        ).toAbsolutePath().normalize();

        try {
            Files.createDirectories(uploadDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể tạo thư mục lưu ảnh sản phẩm",
                    exception
            );
        }
    }

    public String saveImage(MultipartFile image) {

        if (image == null || image.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn ảnh sản phẩm"
            );
        }

        String contentType = image.getContentType();

        if (contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Ảnh sản phẩm chỉ hỗ trợ JPG, PNG hoặc WEBP"
            );
        }

        String originalFilename = image.getOriginalFilename();
        String extension = getExtension(originalFilename);

        String storedFilename =
                UUID.randomUUID() + extension;

        Path targetPath =
                uploadDirectory.resolve(storedFilename).normalize();

        // Ngăn trường hợp đường dẫn thoát khỏi thư mục uploads/products
        if (!targetPath.startsWith(uploadDirectory)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Tên file ảnh không hợp lệ"
            );
        }

        try {
            Files.copy(
                    image.getInputStream(),
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException exception) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể lưu ảnh sản phẩm"
            );
        }

        return "/uploads/products/" + storedFilename;
    }

    private String getExtension(String filename) {

        if (filename == null || filename.isBlank()) {
            return "";
        }

        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex < 0) {
            return "";
        }

        return filename.substring(dotIndex).toLowerCase();
    }
}