package com.badminton.booking.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Lỗi nghiệp vụ do hệ thống chủ động tạo
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request) {

        HttpStatus status = exception.getStatus();

        return buildResponse(
                status,
                exception.getMessage(),
                request
        );
    }

    // JSON gửi lên không đúng định dạng
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Dữ liệu JSON không hợp lệ",
                request
        );
    }

    // Thiếu RequestParam bắt buộc
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Thiếu tham số: " + exception.getParameterName(),
                request
        );
    }

    // Sai kiểu dữ liệu, ví dụ sessionId=ABC
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Tham số " + exception.getName()
                        + " không đúng định dạng",
                request
        );
    }

    // DTO không vượt qua validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Dữ liệu không hợp lệ");

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                message,
                request
        );
    }

    // Trùng UNIQUE hoặc vi phạm ràng buộc database
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "Dữ liệu đã tồn tại hoặc vi phạm ràng buộc",
                request
        );
    }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
                Exception exception,
                HttpServletRequest request) {

        // In lỗi thật ra terminal để kiểm tra nguyên nhân lỗi 500
        exception.printStackTrace();

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi hệ thống: "
                        + exception.getMessage(),
                request
        );
        }
    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }
                // Gửi sai Content-Type, ví dụ gửi JSON cho API multipart/form-data
        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
                HttpMediaTypeNotSupportedException exception,
                HttpServletRequest request) {

        return buildResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "API này yêu cầu multipart/form-data, không nhận raw JSON",
                request
        );
        }

        // Thiếu file trong multipart/form-data
        @ExceptionHandler(MissingServletRequestPartException.class)
        public ResponseEntity<ApiErrorResponse> handleMissingRequestPart(
                MissingServletRequestPartException exception,
                HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Thiếu dữ liệu multipart: "
                        + exception.getRequestPartName(),
                request
        );
        }

        // File vượt quá dung lượng quy định
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
                MaxUploadSizeExceededException exception,
                HttpServletRequest request) {

        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Ảnh sản phẩm không được vượt quá 5 MB",
                request
        );

        }













































}