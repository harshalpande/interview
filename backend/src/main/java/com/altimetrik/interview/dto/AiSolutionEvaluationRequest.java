package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSolutionEvaluationRequest {
    private String sessionId;
    private String technology;
    private String targetRole;
    private Integer yearsOfExperience;
    private String difficulty;
    private Integer questionNumber;
    private String questionTitle;
    private String problemStatement;
    private String originalProblemStatement;
    private String originalStarterCode;
    private String referenceSolution;
    private String expectedTimeComplexity;
    private String expectedSpaceComplexity;
    private String questionIntegrityNotes;
    private String questionPolicy;
    private String evaluationRubric;
    private List<String> expectedConcepts;
    private List<String> nonNegotiableSignals;
    private String code;
    private String stdout;
    private String stderr;
    private Integer exitStatus;
    private Long executionTimeMs;
    private Long solveDurationSeconds;
    private Integer executeAttemptCount;
}
