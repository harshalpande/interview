package com.altimetrik.interview.dto;

import lombok.Data;

@Data
public class AiInterviewerQuestionDraftRequest {
    private String complexityDirection;
    private String sectionMode;
    private String currentSection;
}
