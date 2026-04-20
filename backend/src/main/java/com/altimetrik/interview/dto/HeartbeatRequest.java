package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HeartbeatRequest {
    @NotNull
    private ParticipantRole role;

    @NotBlank
    private String deviceId;
}
