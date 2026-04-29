package com.altimetrik.interview.ai.dto;

import java.util.List;

public record AiSolutionEvaluationRequest(
        String sessionId,
        String technology,
        String targetRole,
        Integer yearsOfExperience,
        String difficulty,
        Integer questionNumber,
        String questionTitle,
        String problemStatement,
        String originalProblemStatement,
        String originalStarterCode,
        String referenceSolution,
        String expectedTimeComplexity,
        String expectedSpaceComplexity,
        String questionIntegrityNotes,
        String questionPolicy,
        String evaluationRubric,
        List<String> expectedConcepts,
        List<String> nonNegotiableSignals,
        String code,
        String stdout,
        String stderr,
        Integer exitStatus,
        Long executionTimeMs,
        Long solveDurationSeconds,
        Integer executeAttemptCount
) {
}
