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
import com.badminton.booking.dto.ForgotPasswordRequest;
import com.badminton.booking.dto.ResetPasswordRequest;
import com.badminton.booking.entity.PasswordResetOtp;
import com.badminton.booking.repository.PasswordResetOtpRepository;
import com.badminton.booking.dto.PhoneVerificationRequest;
import com.badminton.booking.dto.VerifyPhoneRequest;
import com.badminton.booking.entity.PhoneVerificationOtp;
import com.badminton.booking.repository.PhoneVerificationOtpRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifierService
        googleTokenVerifierService;

        private final PasswordResetOtpRepository passwordResetOtpRepository;
        private final SecureRandom secureRandom = new SecureRandom();
        private final PhoneVerificationOtpRepository phoneVerificationOtpRepository;

        public AuthService(
                UserRepository userRepository,
                PasswordEncoder passwordEncoder,
                JwtService jwtService,
                GoogleTokenVerifierService googleTokenVerifierService,
                PasswordResetOtpRepository passwordResetOtpRepository,
                PhoneVerificationOtpRepository phoneVerificationOtpRepository) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleTokenVerifierService = googleTokenVerifierService;
        this.passwordResetOtpRepository = passwordResetOtpRepository;
        this.phoneVerificationOtpRepository =
        phoneVerificationOtpRepository;
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

        @Transactional
        public void requestPasswordResetOtp(
                ForgotPasswordRequest request) {

        if (request == null) {
                return;
        }

        String phone = normalizePhone(request.getPhone());

        User user = userRepository.findByPhone(phone)
                .orElse(null);

        // Không tiết lộ số điện thoại có tài khoản hay không
        if (user == null) {
                return;
        }

        if (!"PHONE".equals(user.getAuthProvider())) {
                return;
        }

        LocalDateTime now = LocalDateTime.now();

        PasswordResetOtp previousOtp =
                passwordResetOtpRepository
                        .findTopByUser_IdAndUsedFalseOrderByCreatedAtDesc(
                                user.getId()
                        )
                        .orElse(null);

        // Chỉ được yêu cầu lại sau 60 giây
        if (previousOtp != null
                && previousOtp.getCreatedAt()
                .plusSeconds(60)
                .isAfter(now)) {

                throw new BusinessException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Vui lòng chờ 60 giây trước khi gửi lại OTP"
                );
        }

        // Vô hiệu hóa OTP cũ
        if (previousOtp != null) {
                previousOtp.setUsed(true);
                passwordResetOtpRepository.save(previousOtp);
        }

        String otp = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        PasswordResetOtp resetOtp =
                new PasswordResetOtp();

        resetOtp.setUser(user);
        resetOtp.setOtpHash(
                passwordEncoder.encode(otp)
        );
        resetOtp.setExpiresAt(
                now.plusMinutes(5)
        );
        resetOtp.setFailedAttempts(0);
        resetOtp.setUsed(false);

        passwordResetOtpRepository.save(resetOtp);

        // CHỈ DÙNG KHI DEVELOPMENT
        // Sau này thay bằng dịch vụ gửi SMS
        System.out.println(
                "OTP đặt lại mật khẩu cho "
                        + phone
                        + ": "
                        + otp
        );
        }

        @Transactional
        public void resetPassword(
                ResetPasswordRequest request) {

        if (request == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Dữ liệu đặt lại mật khẩu không hợp lệ"
                );
        }

        String phone = normalizePhone(request.getPhone());

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.BAD_REQUEST,
                                "OTP không hợp lệ hoặc đã hết hạn"
                        )
                );

        if (!"PHONE".equals(user.getAuthProvider())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "OTP không hợp lệ hoặc đã hết hạn"
                );
        }

        PasswordResetOtp resetOtp =
                passwordResetOtpRepository
                        .findTopByUser_IdAndUsedFalseOrderByCreatedAtDesc(
                                user.getId()
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        HttpStatus.BAD_REQUEST,
                                        "OTP không hợp lệ hoặc đã hết hạn"
                                )
                        );

        LocalDateTime now = LocalDateTime.now();

        if (!resetOtp.getExpiresAt().isAfter(now)) {
                resetOtp.setUsed(true);
                passwordResetOtpRepository.save(resetOtp);

                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "OTP không hợp lệ hoặc đã hết hạn"
                );
        }

        if (resetOtp.getFailedAttempts() >= 5) {
                resetOtp.setUsed(true);
                passwordResetOtpRepository.save(resetOtp);

                throw new BusinessException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "OTP đã bị khóa do nhập sai quá nhiều lần"
                );
        }

        String suppliedOtp = request.getOtp();

        if (suppliedOtp == null
                || !suppliedOtp.matches("^\\d{6}$")
                || !passwordEncoder.matches(
                        suppliedOtp,
                        resetOtp.getOtpHash()
                )) {

                resetOtp.setFailedAttempts(
                        resetOtp.getFailedAttempts() + 1
                );

                if (resetOtp.getFailedAttempts() >= 5) {
                resetOtp.setUsed(true);
                }

                passwordResetOtpRepository.save(resetOtp);

                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "OTP không hợp lệ hoặc đã hết hạn"
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

        user.setPassword(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );

        resetOtp.setUsed(true);

        userRepository.save(user);
        passwordResetOtpRepository.save(resetOtp);
        }


        @Transactional
        public void requestPhoneVerification(
                Long userId,
                PhoneVerificationRequest request) {

        if (request == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Dữ liệu xác minh không hợp lệ"
                );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy tài khoản"
                        )
                );

        if ("SUSPENDED".equals(user.getStatus())) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản đã bị khóa"
                );
        }

        String phone = normalizePhone(request.getPhone());

        User phoneOwner = userRepository.findByPhone(phone)
                .orElse(null);

        // Không cho Gmail khác sử dụng lại số điện thoại
        // đã thuộc một user khác
        if (phoneOwner != null
                && !phoneOwner.getId().equals(userId)) {

                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Số điện thoại này đã được sử dụng bởi tài khoản khác"
                );
        }

        if (phone.equals(user.getPhone())
                && Boolean.TRUE.equals(user.getPhoneVerified())) {

                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Số điện thoại đã được xác minh"
                );
        }

        LocalDateTime now = LocalDateTime.now();

        PhoneVerificationOtp previousOtp =
                phoneVerificationOtpRepository
                        .findTopByUser_IdAndUsedFalseOrderByCreatedAtDesc(
                                userId
                        )
                        .orElse(null);

        if (previousOtp != null
                && previousOtp.getCreatedAt()
                .plusSeconds(60)
                .isAfter(now)) {

                throw new BusinessException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Vui lòng chờ 60 giây trước khi gửi lại OTP"
                );
        }

        if (previousOtp != null) {
                previousOtp.setUsed(true);
                phoneVerificationOtpRepository.save(previousOtp);
        }

        String otp = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        PhoneVerificationOtp verificationOtp =
                new PhoneVerificationOtp();

        verificationOtp.setUser(user);
        verificationOtp.setPendingPhone(phone);
        verificationOtp.setOtpHash(
                passwordEncoder.encode(otp)
        );
        verificationOtp.setExpiresAt(
                now.plusMinutes(5)
        );
        verificationOtp.setFailedAttempts(0);
        verificationOtp.setUsed(false);

        phoneVerificationOtpRepository.save(
                verificationOtp
        );

        // Chỉ dùng để test development
        System.out.println(
                "OTP xác minh số điện thoại "
                        + phone
                        + ": "
                        + otp
        );
        }



        @Transactional
        public void verifyPhone(
                Long userId,
                VerifyPhoneRequest request) {

        if (request == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Dữ liệu xác minh không hợp lệ"
                );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy tài khoản"
                        )
                );

        if ("SUSPENDED".equals(user.getStatus())) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản đã bị khóa"
                );
        }

        PhoneVerificationOtp verificationOtp =
                phoneVerificationOtpRepository
                        .findTopByUser_IdAndUsedFalseOrderByCreatedAtDesc(
                                userId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        HttpStatus.BAD_REQUEST,
                                        "OTP không hợp lệ hoặc đã hết hạn"
                                )
                        );

        LocalDateTime now = LocalDateTime.now();

        if (!verificationOtp.getExpiresAt().isAfter(now)) {
                verificationOtp.setUsed(true);
                phoneVerificationOtpRepository.save(
                        verificationOtp
                );

                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "OTP không hợp lệ hoặc đã hết hạn"
                );
        }

        if (verificationOtp.getFailedAttempts() >= 5) {
                verificationOtp.setUsed(true);
                phoneVerificationOtpRepository.save(
                        verificationOtp
                );

                throw new BusinessException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "OTP đã bị khóa do nhập sai quá nhiều lần"
                );
        }

        String suppliedOtp = request.getOtp();

        if (suppliedOtp == null
                || !suppliedOtp.matches("^\\d{6}$")
                || !passwordEncoder.matches(
                        suppliedOtp,
                        verificationOtp.getOtpHash()
                )) {

                verificationOtp.setFailedAttempts(
                        verificationOtp.getFailedAttempts() + 1
                );

                if (verificationOtp.getFailedAttempts() >= 5) {
                verificationOtp.setUsed(true);
                }

                phoneVerificationOtpRepository.save(
                        verificationOtp
                );

                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "OTP không hợp lệ hoặc đã hết hạn"
                );
        }

        String verifiedPhone =
                verificationOtp.getPendingPhone();

        User phoneOwner = userRepository
                .findByPhone(verifiedPhone)
                .orElse(null);

        // Kiểm tra lại để tránh hai request đồng thời
        if (phoneOwner != null
                && !phoneOwner.getId().equals(userId)) {

                verificationOtp.setUsed(true);
                phoneVerificationOtpRepository.save(
                        verificationOtp
                );

                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Số điện thoại này đã được sử dụng bởi tài khoản khác"
                );
        }

        user.setPhone(verifiedPhone);
        user.setPhoneVerified(true);
        verificationOtp.setUsed(true);

        userRepository.save(user);
        phoneVerificationOtpRepository.save(
                verificationOtp
        );
        }
}