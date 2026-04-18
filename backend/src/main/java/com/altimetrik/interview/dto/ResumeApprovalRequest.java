package com.altimetrik.interview.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResumeApprovalRequest {
    @NotBlank
    private String interviewerName;

    @Email
    @NotBlank
    private String interviewerEmail;
}
