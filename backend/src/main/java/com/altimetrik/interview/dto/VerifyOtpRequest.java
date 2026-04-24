package com.altimetrik.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9]{5}$", message = "must be a 5-character uppercase alphanumeric code")
    private String otp;
}
