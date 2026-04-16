package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.FeedbackRating;
import com.altimetrik.interview.enums.RecommendationDecision;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class FeedbackDto {
    private FeedbackRating rating;
    private String comments;
    private RecommendationDecision recommendationDecision;
    private OffsetDateTime submittedAt;
}
