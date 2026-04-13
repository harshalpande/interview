package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.SessionStatus;
import lombok.Data;

import java.util.List;

/**
 * DTO for updating session status or content.
 */
@Data
public class UpdateSessionRequest {
    private SessionStatus status;
    private List<QuestionDto> questions;
}
