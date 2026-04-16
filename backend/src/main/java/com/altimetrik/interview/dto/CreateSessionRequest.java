package com.altimetrik.interview.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import com.altimetrik.interview.enums.TechnologySkill;

@Data
public class CreateSessionRequest {
    
    @NotBlank
    private String interviewerName;
    
    @Email
    private String interviewerEmail;
    
    @NotBlank
    private String intervieweeName;
    
    @Email
    private String intervieweeEmail;
    
    private String title;

    private TechnologySkill technology;

    private String interviewerTimeZone;
}
