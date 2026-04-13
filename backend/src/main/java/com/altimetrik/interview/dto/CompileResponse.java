package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for compilation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompileResponse {
    private boolean success;
    private List<String> compileErrors;
    private List<String> compileWarnings;
    private String message;
}
