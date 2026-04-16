package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.FeedbackRating;
import com.altimetrik.interview.enums.RecommendationDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotNull
    private FeedbackRating rating;

    @NotBlank
    @Size(max = 4000)
    private String comments;

    @NotNull
    private RecommendationDecision recommendationDecision;
}
