package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CodeUpdateRequest {
    @NotBlank
    private String code;

    @NotNull
    private Long version;

    @NotNull
    private ParticipantRole updatedByRole;
}
