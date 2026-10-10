package com.badminton.booking.config;

import java.util.List;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    configuration.setAllowedOrigins(
      List.of("http://localhost:5173", "http://127.0.0.1:5173")
    );

    configuration.setAllowedMethods(
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    );

    configuration.setAllowedHeaders(
      List.of("Authorization", "Content-Type", "Accept")
    );

    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source =
      new UrlBasedCorsConfigurationSource();

    source.registerCorsConfiguration("/api/**", configuration);

    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    JwtAuthenticationConverter jwtAuthenticationConverter
  ) throws Exception {
    http
      .cors(cors -> cors.configurationSource(corsConfigurationSource()))
      .csrf(csrf -> csrf.disable())

      .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )

      .authorizeHttpRequests(auth ->
        auth

          // Đăng ký và đăng nhập
          .requestMatchers("/api/auth/**")
          .permitAll()

          // API đăng ký/đăng nhập cũ, tạm thời giữ lại
          .requestMatchers(
            HttpMethod.POST,
            "/api/users/register",
            "/api/users/login"
          )
          .permitAll()

          // Dữ liệu sân và lịch chơi công khai
          .requestMatchers(
            HttpMethod.GET,
            "/api/test",
            "/api/court-types/**",
            "/api/rooms/**",
            "/api/courts/**",
            "/api/daily-visitor-schedules/**",
            "/api/daily-visitor-sessions/**"
          )
          .permitAll()

          // Chỉ ADMIN được tạo ADMIN/STAFF
          .requestMatchers(
            HttpMethod.POST,
            "/api/users/admin",
            "/api/users/staff"
          )
          .hasRole("ADMIN")
          // CUSTOMER xem booking của chính mình
          .requestMatchers(HttpMethod.GET, "/api/bookings/my")
          .hasRole("CUSTOMER")
          // Chỉ STAFF/ADMIN được thao tác tại quầy
          .requestMatchers(
            HttpMethod.POST,
            "/api/bookings/*/check-in",
            "/api/bookings/walk-in",
            "/api/bookings/*/complete",
            "/api/bookings/*/items",
            "/api/bookings/*/items/*/cancel",
            "/api/bookings/*/settle",
            "/api/daily-visitor-participants/register-walk-in",
            "/api/daily-visitor-participants/*/check-in"
          )
          .hasAnyRole("STAFF", "ADMIN")

          // Chỉ STAFF/ADMIN được xem toàn bộ booking
          .requestMatchers(
            HttpMethod.GET,
            "/api/bookings",
            "/api/daily-visitor-participants/session/**"
          )
          .hasAnyRole("STAFF", "ADMIN")

          // Chỉ CUSTOMER được tự đặt sân
          .requestMatchers(
            HttpMethod.POST,
            "/api/bookings",
            "/api/daily-visitor-participants/register"
          )
          .hasRole("CUSTOMER")

          // Chỉ CUSTOMER được gọi API tự hủy booking
          .requestMatchers(HttpMethod.DELETE, "/api/bookings/*")
          .hasRole("CUSTOMER")

          // Chỉ ADMIN được tạo sản phẩm
          // Đường dẫn cụ thể phải đặt trước đường dẫn tổng quát.
          .requestMatchers(HttpMethod.GET, "/api/products/admin")
          .hasRole("ADMIN")

          .requestMatchers(HttpMethod.GET, "/api/products/**")
          .hasAnyRole("STAFF", "ADMIN")

          // Chỉ ADMIN được xem cả sản phẩm đã ngừng bán.
          .requestMatchers(HttpMethod.GET, "/api/products/admin")
          .hasRole("ADMIN")

          // Chỉ ADMIN được sửa thông tin sản phẩm.
          .requestMatchers(HttpMethod.PUT, "/api/products/*")
          .hasRole("ADMIN")

          // Chỉ ADMIN được ngừng bán hoặc bán lại sản phẩm.
          .requestMatchers(HttpMethod.PATCH, "/api/products/*/active")
          .hasRole("ADMIN")

          .requestMatchers(
            HttpMethod.GET,
            "/uploads/products/**",
            "/uploads/avatars/**"
          )
          .permitAll()

          .requestMatchers(
            HttpMethod.POST,
            "/api/inventory-batches",
            "/api/inventory-batches/*/receive"
          )
          .hasRole("ADMIN")

          .requestMatchers(HttpMethod.GET, "/api/inventory-batches/**")
          .hasAnyRole("STAFF", "ADMIN")

          .requestMatchers(HttpMethod.GET, "/google-login-test.html")
          .permitAll()

          .requestMatchers(HttpMethod.POST, "/api/suppliers")
          .hasRole("ADMIN")

          .requestMatchers(HttpMethod.GET, "/api/suppliers/**")
          .hasAnyRole("STAFF", "ADMIN")

          .requestMatchers(
            HttpMethod.POST,
            "/api/inventory-batches/counter-sales/pieces"
          )
          .hasAnyRole("STAFF", "ADMIN")

          // Chỉ ADMIN được xem và sửa bảng giá
          .requestMatchers("/api/admin/prices/**")
          .hasRole("ADMIN")

          .requestMatchers("/api/dashboard/staff-cash/**")
          .hasAnyRole("STAFF", "ADMIN")

          .requestMatchers("/api/admin/users/**")
          .hasRole("ADMIN")

          .requestMatchers("/api/admin/staff-performance/**")
          .hasRole("ADMIN")

          // STAFF/ADMIN trả lời review
          .requestMatchers(HttpMethod.POST, "/api/court-reviews/*/reply")
          .hasAnyRole("STAFF", "ADMIN")

          // CUSTOMER sử dụng danh sách chờ của chính mình.
          .requestMatchers("/api/daily-visitor-waitlists/**")
          .hasRole("CUSTOMER")

          // CUSTOMER hủy slot đánh vãng lai của chính mình.
          .requestMatchers(
            HttpMethod.DELETE,
            "/api/daily-visitor-participants/*/cancel"
          )
          .hasRole("CUSTOMER")

          // STAFF/ADMIN hủy đăng ký khách tại quầy.
          .requestMatchers(
            HttpMethod.DELETE,
            "/api/daily-visitor-participants/*/staff-cancel",
            "/api/bookings/*/cancel-walk-in"
          )
          .hasAnyRole("STAFF", "ADMIN")

          .requestMatchers(HttpMethod.POST, "/api/products/images")
          .hasRole("ADMIN")

          .requestMatchers("/api/staff/dashboard/**")
          .hasAnyRole("STAFF", "ADMIN")

          // Các API còn lại phải đăng nhập
          .anyRequest()
          .authenticated()
      )

      .oauth2ResourceServer(oauth2 ->
        oauth2.jwt(jwt ->
          jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
        )
      );

    return http.build();
  }
}
