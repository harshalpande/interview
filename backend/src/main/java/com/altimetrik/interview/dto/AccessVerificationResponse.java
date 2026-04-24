package com.altimetrik.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class AccessVerificationResponse {
    private boolean success;
    private boolean sessionReadyToStart;
    private boolean retryAvailable;
    private int remainingOtpWindows;
    private OffsetDateTime otpExpiresAt;
    private String message;
    private AccessLinkResponse access;
    private SessionResponse session;
}
