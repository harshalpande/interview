package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.PreparationAttemptStatus;
import com.altimetrik.interview.enums.TechnologySkill;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(
        name = "preparation_attempt",
        indexes = {
                @Index(name = "idx_preparation_attempt_email_created", columnList = "emailNormalized,createdAt"),
                @Index(name = "idx_preparation_attempt_token", columnList = "secureToken"),
                @Index(name = "idx_preparation_attempt_status", columnList = "status")
        }
)
public class PreparationAttempt {

    @Id
    private String id;

    private String candidateName;

    private String email;

    private String emailNormalized;

    @Enumerated(EnumType.STRING)
    private TechnologySkill technology;

    private Integer yearsOfExperience;

    private String experienceBand;

    private String targetRole;

    private String secureToken;

    private String otpHash;

    private OffsetDateTime otpIssuedAt;

    private OffsetDateTime otpExpiresAt;

    private OffsetDateTime otpVerifiedAt;

    private Integer otpResendCount = 0;

    private OffsetDateTime disclaimerAcceptedAt;

    private OffsetDateTime linkExpiresAt;

    @Enumerated(EnumType.STRING)
    private PreparationAttemptStatus status = PreparationAttemptStatus.OTP_PENDING;

    private String currentQuestionId;

    private String currentSeriesId;

    private Integer currentSequenceNumber;

    private OffsetDateTime questionStartedAt;

    private OffsetDateTime questionExpiresAt;

    private OffsetDateTime completedAt;

    private String completionReason;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
