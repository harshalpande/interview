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
public class AiSolutionEvaluationResponse {
    private Integer correctnessScore;
    private Integer codeQualityScore;
    private Integer edgeCaseScore;
    private Integer efficiencyScore;
    private Integer overallScore;
    private String verdict;
    private String nextDifficulty;
    private Integer nextDifficultyLevel;
    private String summary;
    private String complexityAssessment;
    private String questionIntegrityNotes;
    private List<String> strengths;
    private List<String> concerns;
}
