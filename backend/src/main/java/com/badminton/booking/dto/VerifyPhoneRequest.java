package com.badminton.booking.dto;

public class VerifyPhoneRequest {

    private String otp;

    public VerifyPhoneRequest() {
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}