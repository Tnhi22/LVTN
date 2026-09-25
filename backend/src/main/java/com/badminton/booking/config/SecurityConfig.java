package com.badminton.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return authenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Đăng ký và đăng nhập
                        .requestMatchers("/api/auth/**")
                        .permitAll()

                        // API đăng ký/đăng nhập cũ, tạm thời giữ lại
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users/register",
                                "/api/users/login"
                        ).permitAll()

                        // Dữ liệu sân và lịch chơi công khai
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/test",
                                "/api/court-types/**",
                                "/api/rooms/**",
                                "/api/courts/**",
                                "/api/daily-visitor-schedules/**",
                                "/api/daily-visitor-sessions/**"
                        ).permitAll()

                        // Chỉ ADMIN được tạo ADMIN/STAFF
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users/admin",
                                "/api/users/staff"
                        ).hasRole("ADMIN")
                        // CUSTOMER xem booking của chính mình
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/bookings/my"
                        ).hasRole("CUSTOMER")
                                                // Chỉ STAFF/ADMIN được thao tác tại quầy
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/bookings/*/check-in",
                                "/api/bookings/walk-in",
                                "/api/daily-visitor-participants/register-walk-in",
                                "/api/daily-visitor-participants/*/check-in"
                        ).hasAnyRole("STAFF", "ADMIN")

                        // Chỉ STAFF/ADMIN được xem toàn bộ booking
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/bookings",
                                "/api/daily-visitor-participants/session/**"
                        ).hasAnyRole("STAFF", "ADMIN")

                        // Chỉ CUSTOMER được tự đặt sân
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/bookings",
                                "/api/daily-visitor-participants/register"
                        ).hasRole("CUSTOMER")

                        // Chỉ CUSTOMER được gọi API tự hủy booking
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/bookings/*"
                        ).hasRole("CUSTOMER")



                        // Chỉ ADMIN được tạo sản phẩm
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products"
                        ).hasRole("ADMIN")

                        // STAFF và ADMIN được xem sản phẩm
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/products/**"
                        ).hasAnyRole("STAFF", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/uploads/products/**"
                        ).permitAll()


                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/inventory-batches",
                                "/api/inventory-batches/*/receive"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/inventory-batches/**"
                        ).hasAnyRole("STAFF", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/google-login-test.html"
                        ).permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/suppliers")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/suppliers/**")
                        .hasAnyRole("STAFF", "ADMIN")

                        .requestMatchers(
                        HttpMethod.POST,
                        "/api/inventory-batches/counter-sales/pieces"
                        ).hasAnyRole("STAFF", "ADMIN")

                        // Các API còn lại phải đăng nhập
                        .anyRequest().authenticated()
                )

                        .oauth2ResourceServer(oauth2 ->
                                oauth2.jwt(jwt ->
                                        jwt.jwtAuthenticationConverter(
                                                jwtAuthenticationConverter
                                        )
                                )
                        );

        return http.build();
    }


}