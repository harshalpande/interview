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
public class AiInterviewRecommendationRequest {
    private String sessionId;
    private String technology;
    private String targetRole;
    private Integer yearsOfExperience;
    private Integer maxQuestions;
    private String recommendationPolicy;
    private String evaluationRubric;
    private List<AiSolutionEvaluationRequest> questionResults;
}
