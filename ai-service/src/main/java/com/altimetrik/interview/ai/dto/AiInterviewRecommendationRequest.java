package com.altimetrik.interview.ai.dto;

import java.util.List;

public record AiInterviewRecommendationRequest(
        String sessionId,
        String technology,
        String targetRole,
        Integer yearsOfExperience,
        Integer maxQuestions,
        String recommendationPolicy,
        String evaluationRubric,
        List<AiSolutionEvaluationRequest> questionResults
) {
}
