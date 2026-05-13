package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

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
    private Integer difficultyLevel;
    private Integer idealDurationMinutes;
    @JsonIgnore
    private String questionBankId;
    @JsonIgnore
    private String questionSeriesId;
    @JsonIgnore
    private Integer questionSequenceNumber;
    private String expectedTimeComplexity;
    private String expectedSpaceComplexity;
    private String questionIntegrityNotes;
    @JsonIgnore
    private String originalProblemStatement;
    @JsonIgnore
    private String originalStarterCode;
    @JsonIgnore
    private String referenceSolution;
    @JsonIgnore
    private String questionConcepts;
    @JsonIgnore
    private String questionEvaluationFocus;
    private OffsetDateTime candidateStartedAt;
    private OffsetDateTime submittedAt;
    private Long solveDurationSeconds;
    private Integer executeAttemptCount;
    private RunResultDto runResult;
    private AiSolutionEvaluationResponse aiEvaluation;
    private Boolean changedAfterLastRun;
}
