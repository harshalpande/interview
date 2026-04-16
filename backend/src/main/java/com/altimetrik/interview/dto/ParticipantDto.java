package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import com.altimetrik.interview.enums.IdentityCaptureFailureReason;
import com.altimetrik.interview.enums.IdentityCaptureStatus;
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
}
