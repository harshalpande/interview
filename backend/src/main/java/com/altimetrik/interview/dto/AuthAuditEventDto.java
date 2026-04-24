package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class AuthAuditEventDto {
    private OffsetDateTime createdAt;
    private ParticipantRole participantRole;
    private String title;
    private String detail;
}
