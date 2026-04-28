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
public class AiQuestionResponse {
    private String title;
    private String filePath;
    private String displayName;
    private String problemStatement;
    private String starterCode;
    private String difficulty;
    private Integer difficultyLevel;
    private Integer idealDurationMinutes;
    private String referenceSolution;
    private String expectedTimeComplexity;
    private String expectedSpaceComplexity;
    private List<String> concepts;
    private List<String> evaluationFocus;
}
