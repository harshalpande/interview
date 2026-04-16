package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ActivityEventType;
import com.altimetrik.interview.enums.ParticipantRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class ActivityEventDto {
    private String id;
    private ParticipantRole participantRole;
    private ActivityEventType eventType;
    private String detail;
    private OffsetDateTime createdAt;
}
