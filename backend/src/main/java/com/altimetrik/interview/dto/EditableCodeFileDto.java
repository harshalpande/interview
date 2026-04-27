package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditableCodeFileDto {
    private String path;
    private String displayName;
    private String content;
    private Boolean editable;
    private Integer sortOrder;
    private Boolean enabledForCandidate;
    private Boolean activeQuestion;
    private Boolean submitted;
    private Integer idealDurationMinutes;
    private OffsetDateTime candidateStartedAt;
    private OffsetDateTime submittedAt;
    private Long solveDurationSeconds;
    private Integer executeAttemptCount;
    private RunResultDto runResult;
    private Boolean changedAfterLastRun;
}
