package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.PreparationAttemptStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class PreparationAccessResponse {
    private String attemptId;
    private String candidateName;
    private String email;
    private Integer yearsOfExperience;
    private String experienceBand;
    private PreparationAttemptStatus status;
    private boolean otpVerified;
    private boolean disclaimerAccepted;
    private OffsetDateTime disclaimerAcceptedAt;
    private OffsetDateTime linkExpiresAt;
    private OffsetDateTime otpExpiresAt;
    private OffsetDateTime attemptExpiresAt;
    private long remainingAttemptSeconds;
    private Integer remainingOtpResends;
    private String message;
}
