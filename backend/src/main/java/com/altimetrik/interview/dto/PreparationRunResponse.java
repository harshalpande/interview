package com.altimetrik.interview.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PreparationRunResponse {
    private ExecuteResponse execution;
    private boolean passed;
    private boolean attemptEnded;
    private String message;
    private PreparationQuestionResponse question;
}
