package com.badminton.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PhoneRegisterRequest {

    @NotBlank(message = "Vui lòng nhập họ tên")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(
            regexp = "^(?:0|\\+84)[35789]\\d{8}$",
            message = "Số điện thoại không đúng định dạng Việt Nam"
    )
    private String phone;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size(
            min = 8,
            max = 72,
            message = "Mật khẩu phải có từ 8 đến 72 ký tự"
    )
    private String password;

    public PhoneRegisterRequest() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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