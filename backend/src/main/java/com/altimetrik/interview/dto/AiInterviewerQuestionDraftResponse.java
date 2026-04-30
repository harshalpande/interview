package com.altimetrik.interview.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiInterviewerQuestionDraftResponse {
    private String draftId;
    private String section;
    private AiQuestionResponse question;
}
