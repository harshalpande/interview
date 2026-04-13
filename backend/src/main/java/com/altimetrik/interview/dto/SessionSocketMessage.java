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
    private String message;
}
