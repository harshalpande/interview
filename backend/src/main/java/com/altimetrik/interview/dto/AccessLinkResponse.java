package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.AvMode;
import com.altimetrik.interview.enums.ParticipantAccessStatus;
import com.altimetrik.interview.enums.ParticipantRole;
import com.altimetrik.interview.enums.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class AccessLinkResponse {
    private String sessionId;
    private ParticipantRole role;
    private String participantName;
    private String participantEmail;
    private AvMode avMode;
    private SessionStatus sessionStatus;
    private ParticipantAccessStatus accessStatus;
    private OffsetDateTime otpExpiresAt;
    private Integer remainingOtpWindows;
    private Boolean disclaimerAccepted;
    private Boolean otpVerified;
    private Boolean identityCaptureRequired;
    private Boolean identityCaptureComplete;
    private Boolean sessionReadyToStart;
    private String message;
}
