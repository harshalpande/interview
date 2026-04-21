package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CodeUpdateRequest {
    private String code;

    @NotNull
    private Long version;

    @NotNull
    private ParticipantRole updatedByRole;

    private List<EditableCodeFileDto> codeFiles;
}
