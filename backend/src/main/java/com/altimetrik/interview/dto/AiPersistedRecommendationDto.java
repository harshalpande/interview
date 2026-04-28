package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPersistedRecommendationDto {
    private String rating;
    private String recommendationDecision;
    private Integer overallScore;
    private String summary;
    private List<String> strengths;
    private List<String> risks;
    private List<String> suggestedFollowUps;
    private Boolean humanReviewRequired;
    private OffsetDateTime generatedAt;
}

