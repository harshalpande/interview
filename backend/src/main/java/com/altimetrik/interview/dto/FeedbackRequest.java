package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.FeedbackRating;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotNull
    private FeedbackRating rating;

    @NotBlank
    private String comments;

    @NotNull
    private Boolean recommendation;
}
