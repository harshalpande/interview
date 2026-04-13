package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AcceptDisclaimerRequest {
    @NotNull
    private ParticipantRole role;
}
