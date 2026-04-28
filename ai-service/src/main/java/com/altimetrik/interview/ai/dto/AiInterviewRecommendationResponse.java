package com.altimetrik.interview.ai.dto;

import java.util.List;

public record AiInterviewRecommendationResponse(
        String rating,
        String recommendationDecision,
        Integer overallScore,
        String summary,
        List<String> strengths,
        List<String> risks,
        List<String> suggestedFollowUps,
        Boolean humanReviewRequired
) {
}

