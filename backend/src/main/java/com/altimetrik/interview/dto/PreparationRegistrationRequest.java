package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.TechnologySkill;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PreparationRegistrationRequest {
    @NotBlank
    private String candidateName;

    @NotBlank
    @Email
    private String email;

    @NotNull
    private TechnologySkill technology;

    @NotNull
    @Min(0)
    @Max(50)
    private Integer yearsOfExperience;

    @NotBlank
    private String targetRole;
}
