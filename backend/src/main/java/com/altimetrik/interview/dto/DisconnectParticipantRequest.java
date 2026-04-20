package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import com.altimetrik.interview.enums.ResumeReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DisconnectParticipantRequest {
    @NotNull
    private ParticipantRole role;

    @NotBlank
    private String deviceId;

    @NotNull
    private ResumeReason reason;

    private String finalCode;
}
