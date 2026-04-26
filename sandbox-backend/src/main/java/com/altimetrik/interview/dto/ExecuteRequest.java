package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ExecutionLanguage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteRequest {
    private static final long DEFAULT_TIMEOUT_MS = 5000;
    private static final long DEFAULT_MEMORY_LIMIT_MB = 512;

    private String sourceCode;
    @Builder.Default
    private ExecutionLanguage language = ExecutionLanguage.JAVA;
    private String activeFilePath;
    private long timeoutMs;
    private long memoryLimitMb;

    public ExecuteRequest(String sourceCode) {
        this.sourceCode = sourceCode;
        this.language = ExecutionLanguage.JAVA;
        this.timeoutMs = DEFAULT_TIMEOUT_MS;
        this.memoryLimitMb = DEFAULT_MEMORY_LIMIT_MB;
    }
}
