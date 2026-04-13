package com.altimetrik.interview.dto;

import lombok.Data;

/**
 * DTO for candidate responses to questions.
 */
@Data
public class CandidateResponseRequest {
    private String questionId;
    private String sourceCode;
    private String language;
    private String compileOutput;
    private String executionOutput;
    private Long executionTimeMs;
    private String status;
}
