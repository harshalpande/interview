package com.altimetrik.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class JoinTokenResponse {
    private String token;
    private String joinUrl;
    private OffsetDateTime expiresAt;
}
