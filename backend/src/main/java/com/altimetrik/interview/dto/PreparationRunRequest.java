package com.altimetrik.interview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PreparationRunRequest {
    @NotBlank
    private String sourceCode;
}
