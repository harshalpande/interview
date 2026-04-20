package com.altimetrik.interview.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionSocketMessage {
    private String type;
    private String sessionId;
    private Long version;
    private String code;
    private Integer timeLeft;
    private SessionResponse session;
    private ActivityEventDto activityEvent;
    private String message;
    private String signalType;
    private com.altimetrik.interview.enums.ParticipantRole senderRole;
    private com.altimetrik.interview.enums.ParticipantRole targetRole;
    private String sdp;
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;
    private Boolean cameraEnabled;
    private Boolean microphoneEnabled;
}
