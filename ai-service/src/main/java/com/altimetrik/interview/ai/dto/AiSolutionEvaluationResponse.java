package com.altimetrik.interview.ai.dto;

import java.util.List;

public record AiSolutionEvaluationResponse(
        Integer correctnessScore,
        Integer codeQualityScore,
        Integer edgeCaseScore,
        Integer efficiencyScore,
        Integer overallScore,
        String verdict,
        String nextDifficulty,
        Integer nextDifficultyLevel,
        String summary,
        String complexityAssessment,
        String questionIntegrityNotes,
        List<String> strengths,
        List<String> concerns
) {
}
