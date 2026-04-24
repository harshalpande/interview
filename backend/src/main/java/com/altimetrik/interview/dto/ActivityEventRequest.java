package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ActivityEventType;
import com.altimetrik.interview.enums.ParticipantRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivityEventRequest {
    @NotNull
    private ParticipantRole participantRole;

    @NotNull
    private ActivityEventType eventType;

    @NotBlank
    private String detail;

    private Long durationMs;
}
