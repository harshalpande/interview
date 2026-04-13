package com.altimetrik.interview.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * DTO for interview questions.
 */
@Data
@Builder
public class QuestionDto {
    private String id;
    private String title;
    private String description;
    private String difficulty;
    private List<String> tags;
}
