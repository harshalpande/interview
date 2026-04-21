package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for code execution results.
 * Contains stdout, stderr, compile errors, and exit code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteResponse {
    private boolean success;
    private String stdout;
    private String stderr;
    private List<String> compileErrors;
    private int exitCode;
    private long executionTimeMs;
    private String message;
    private String previewUrl;
}
