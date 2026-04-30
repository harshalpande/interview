package com.altimetrik.interview.dto;

import lombok.Data;

@Data
public class AiInterviewerQuestionAcceptRequest {
    private String activeFilePath;
    private Boolean createNewTab;
}
