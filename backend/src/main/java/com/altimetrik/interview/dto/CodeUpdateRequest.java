package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ParticipantRole;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CodeUpdateRequest {
    @JsonAlias("latestCode")
    private String code;

    @NotNull
    @JsonAlias("codeVersion")
    private Long version;

    @NotNull
    private ParticipantRole updatedByRole;

    private List<EditableCodeFileDto> codeFiles;
}
