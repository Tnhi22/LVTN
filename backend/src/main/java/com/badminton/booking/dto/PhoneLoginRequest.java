package com.badminton.booking.dto;

import jakarta.validation.constraints.NotBlank;

public class PhoneLoginRequest {

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    private String phone;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    private String password;

    public PhoneLoginRequest() {
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}