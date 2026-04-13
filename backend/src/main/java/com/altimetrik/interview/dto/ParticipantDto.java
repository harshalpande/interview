package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class ParticipantDto {
    private ParticipantRole role;
    private String name;
    private String email;
    private OffsetDateTime disclaimerAcceptedAt;
    private OffsetDateTime joinedAt;
}
