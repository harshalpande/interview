package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.ParticipantAccessStatus;
import com.altimetrik.interview.enums.ParticipantRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "participant_access_challenges")
public class ParticipantAccessChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String sessionId;

    @Enumerated(EnumType.STRING)
    private ParticipantRole participantRole;

    private String participantEmail;

    @Column(unique = true, nullable = false)
    private String secureToken;

    private String otpHash;

    private OffsetDateTime otpIssuedAt;

    private OffsetDateTime otpExpiresAt;

    private OffsetDateTime otpVerifiedAt;

    private OffsetDateTime lastEmailSentAt;

    private Integer otpWindowCount = 0;

    @Enumerated(EnumType.STRING)
    private ParticipantAccessStatus status = ParticipantAccessStatus.NOT_STARTED;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    private String lastKnownIp;

    private String lastKnownUserAgent;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
