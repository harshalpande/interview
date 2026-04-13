package com.altimetrik.interview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EndSessionRequest {
    @NotBlank
    private String finalCode;
}
