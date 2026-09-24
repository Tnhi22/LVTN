package com.badminton.booking.dto;

public class AdminUserResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String authProvider;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private String role;
    private String status;
    private long violationCount;

    public AdminUserResponse(
            Long id,
            String fullName,
            String email,
            String phone,
            String avatarUrl,
            String authProvider,
            Boolean emailVerified,
            Boolean phoneVerified,
            String role,
            String status,
            long violationCount) {

        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
        this.authProvider = authProvider;
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.role = role;
        this.status = status;
        this.violationCount = violationCount;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public Boolean getPhoneVerified() {
        return phoneVerified;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public long getViolationCount() {
        return violationCount;
    }
}