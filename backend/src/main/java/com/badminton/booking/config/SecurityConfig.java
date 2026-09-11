package com.badminton.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth

                .requestMatchers("/api/test").permitAll()

                .requestMatchers("/api/court-types").permitAll()

                .requestMatchers("/api/rooms").permitAll()

                .requestMatchers("/api/courts").permitAll()

                .requestMatchers("/api/daily-visitor-schedules/**")
                .permitAll()

                .requestMatchers("/api/daily-visitor-sessions/**")
                .permitAll()

                .requestMatchers("/api/daily-visitor-participants/**")
                .permitAll()

                .requestMatchers("/api/users/register")
                .permitAll()

                .requestMatchers("/api/users/login")
                .permitAll()

                .requestMatchers("/api/bookings/**")
                .permitAll()

                .anyRequest()
                .permitAll()
            );

        return http.build();
    }
}