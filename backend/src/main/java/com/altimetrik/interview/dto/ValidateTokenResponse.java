package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class ValidateTokenResponse {
    private boolean valid;
    private String sessionId;
    private ParticipantRole role;
    private OffsetDateTime expiresAt;
    private String message;
}
