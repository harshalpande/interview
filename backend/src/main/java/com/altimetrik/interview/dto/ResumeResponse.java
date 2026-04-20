package com.altimetrik.interview.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResumeResponse {
    private String status;
    private String message;
    private Boolean approvalRequired;
    private SessionResponse session;

    public static final String APPROVED = "APPROVED";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String REJECTED = "REJECTED";
}
