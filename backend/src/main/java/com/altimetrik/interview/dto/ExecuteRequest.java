package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Java code execution.
 * Contains the Java source code to be compiled and executed in one step.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteRequest {
    private String sourceCode;
    private long timeoutMs;
    private long memoryLimitMb;

    public ExecuteRequest(String sourceCode) {
        this.sourceCode = sourceCode;
        this.timeoutMs = 5000;      // 5 seconds default
        this.memoryLimitMb = 512;   // 512 MB default
    }
}
