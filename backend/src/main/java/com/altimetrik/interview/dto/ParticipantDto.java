package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import com.altimetrik.interview.enums.IdentityCaptureFailureReason;
import com.altimetrik.interview.enums.IdentityCaptureStatus;
import com.altimetrik.interview.enums.ParticipantConnectionStatus;
import com.altimetrik.interview.enums.ResumeReason;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class ParticipantDto {
    private ParticipantRole role;
    private String name;
    private String email;
    private String timeZone;
    private IdentityCaptureStatus identityCaptureStatus;
    private IdentityCaptureFailureReason identityCaptureFailureReason;
    private String identitySnapshotPath;
    private OffsetDateTime identitySnapshotCapturedAt;
    private OffsetDateTime disclaimerAcceptedAt;
    private OffsetDateTime joinedAt;
    private ParticipantConnectionStatus connectionStatus;
    private String deviceId;
    private String lastKnownIp;
    private OffsetDateTime lastSeenAt;
    private OffsetDateTime disconnectedAt;
    private OffsetDateTime resumeRequestedAt;
    private OffsetDateTime resumeApprovedAt;
    private OffsetDateTime resumeRejectedAt;
    private Integer resumeCount;
    private ResumeReason pendingResumeReason;
    private Boolean awaitingResumeApproval;
}
