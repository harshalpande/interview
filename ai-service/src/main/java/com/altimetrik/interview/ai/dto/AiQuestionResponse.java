package com.altimetrik.interview.ai.dto;

import java.util.List;

public record AiQuestionResponse(
        String title,
        String filePath,
        String displayName,
        String problemStatement,
        String starterCode,
        String difficulty,
        Integer difficultyLevel,
        Integer idealDurationMinutes,
        String referenceSolution,
        String expectedTimeComplexity,
        String expectedSpaceComplexity,
        List<String> concepts,
        List<String> evaluationFocus
) {
}
