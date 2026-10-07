package com.badminton.booking.config;

import com.badminton.booking.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Rechecks the database on each authenticated request, including existing JWTs. */
public class AccountStatusValidator implements OAuth2TokenValidator<Jwt> {
    private final UserRepository users;
    public AccountStatusValidator(UserRepository users) { this.users = users; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long id;
        try { id = Long.valueOf(jwt.getSubject()); }
        catch (NumberFormatException exception) { return denied(); }
        return users.findById(id)
                .filter(u -> "ACTIVE".equals(u.getStatus()) || "WARNING".equals(u.getStatus()))
                .filter(u -> u.getRole().equals(jwt.getClaimAsString("role")))
                .map(u -> OAuth2TokenValidatorResult.success()).orElseGet(this::denied);
    }
    private OAuth2TokenValidatorResult denied() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                "Tài khoản đã bị khóa, không tồn tại hoặc quyền đã thay đổi. Vui lòng liên hệ ADMIN.", null));
    }
}
