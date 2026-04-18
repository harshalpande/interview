package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.IdentityCaptureFailureReason;
import com.altimetrik.interview.enums.IdentityCaptureStatus;
import com.altimetrik.interview.enums.ParticipantConnectionStatus;
import com.altimetrik.interview.enums.ResumeReason;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "participants")
public class Participant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String sessionId;
    
    @Enumerated(EnumType.STRING)
    private com.altimetrik.interview.enums.ParticipantRole role;
    
    private String name;
    
    private String email;

    private String timeZone;

    @Enumerated(EnumType.STRING)
    private IdentityCaptureStatus identityCaptureStatus;

    @Enumerated(EnumType.STRING)
    private IdentityCaptureFailureReason identityCaptureFailureReason;

    private String identitySnapshotPath;

    private String identitySnapshotMimeType;

    private OffsetDateTime identitySnapshotCapturedAt;
    
    private OffsetDateTime disclaimerAcceptedAt;
    
    @CreationTimestamp
    private OffsetDateTime joinedAt;

    @Enumerated(EnumType.STRING)
    private ParticipantConnectionStatus connectionStatus = ParticipantConnectionStatus.DISCONNECTED;

    private String deviceId;

    private String userAgent;

    private String lastKnownIp;

    private OffsetDateTime lastSeenAt;

    private OffsetDateTime disconnectedAt;

    private OffsetDateTime resumeRequestedAt;

    private OffsetDateTime resumeApprovedAt;

    private OffsetDateTime resumeRejectedAt;

    private Integer resumeCount = 0;

    @Enumerated(EnumType.STRING)
    private ResumeReason pendingResumeReason;

    private Boolean awaitingResumeApproval = false;
}
