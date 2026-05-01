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
public class AiQuestionGenerationRequest {
    private String sessionId;
    private String technology;
    private String evaluationStyle;
    private Integer yearsOfExperience;
    private String targetRole;
    private String startingDifficulty;
    private String currentDifficulty;
    private Integer questionNumber;
    private Integer maxQuestions;
    private Long timeRemainingSeconds;
    private String variationSeed;
    private Integer idealDurationMinutes;
    private Integer banyanLevel;
    private String previousBanyanChallenge;
    private String questionPolicy;
    private String evaluationRubric;
    private List<String> targetConcepts;
    private List<String> previousQuestionTitles;
    private List<String> previousConcepts;
    private List<String> avoidConcepts;
    private List<String> forbiddenCapabilities;
    private List<String> requiredQuestionElements;
    private String sandboxRules;
}
