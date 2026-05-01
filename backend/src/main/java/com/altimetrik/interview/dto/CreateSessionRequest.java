package com.altimetrik.interview.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import com.altimetrik.interview.enums.AvMode;
import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.InterviewMode;
import com.altimetrik.interview.enums.TechnologySkill;

@Data
public class CreateSessionRequest {
    
    private String interviewerName;
    
    @Email
    private String interviewerEmail;
    
    @NotBlank
    private String intervieweeName;
    
    @Email
    private String intervieweeEmail;
    
    private String title;

    private TechnologySkill technology;

    private InterviewMode interviewMode;

    private EvaluationStyle evaluationStyle;

    private Integer yearsOfExperience;

    private String targetRole;

    @Min(1)
    @Max(5)
    private Integer startingDifficultyLevel;

    private Integer maxQuestions;

    private String interviewerTimeZone;

    private AvMode avMode;
}
