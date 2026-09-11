package com.badminton.booking.dto;

public class LoginResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String role;

    public LoginResponse() {
    }

    public LoginResponse(
            Long id,
            String fullName,
            String email,
            String phone,
            String role) {

        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
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

    public String getRole() {
        return role;
    }
}