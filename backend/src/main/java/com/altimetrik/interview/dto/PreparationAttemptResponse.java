package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.PreparationAttemptStatus;
import com.altimetrik.interview.enums.TechnologySkill;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class PreparationAttemptResponse {
    private String id;
    private String candidateName;
    private String email;
    private TechnologySkill technology;
    private Integer yearsOfExperience;
    private String experienceBand;
    private String targetRole;
    private PreparationAttemptStatus status;
    private OffsetDateTime linkExpiresAt;
    private OffsetDateTime otpIssuedAt;
    private OffsetDateTime otpExpiresAt;
    private OffsetDateTime otpVerifiedAt;
    private OffsetDateTime disclaimerAcceptedAt;
    private Integer remainingOtpResends;
    private OffsetDateTime questionStartedAt;
    private OffsetDateTime questionExpiresAt;
    private OffsetDateTime attemptExpiresAt;
    private long remainingAttemptSeconds;
    private String currentQuestionId;
    private String currentSeriesId;
    private Integer currentSequenceNumber;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String message;
}
