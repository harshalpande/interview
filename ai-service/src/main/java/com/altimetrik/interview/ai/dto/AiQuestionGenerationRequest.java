package com.altimetrik.interview.ai.dto;

import java.util.List;

public record AiQuestionGenerationRequest(
        String sessionId,
        String technology,
        Integer yearsOfExperience,
        String targetRole,
        String startingDifficulty,
        String currentDifficulty,
        Integer questionNumber,
        Integer maxQuestions,
        Long timeRemainingSeconds,
        String variationSeed,
        Integer idealDurationMinutes,
        String questionPolicy,
        String evaluationRubric,
        List<String> targetConcepts,
        List<String> previousQuestionTitles,
        List<String> previousConcepts,
        List<String> avoidConcepts,
        List<String> forbiddenCapabilities,
        List<String> requiredQuestionElements,
        String sandboxRules
) {
}
