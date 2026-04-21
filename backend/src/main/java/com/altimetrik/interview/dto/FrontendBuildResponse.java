package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrontendBuildResponse {
    private boolean success;
    private String stdout;
    private String stderr;
    private List<String> compileErrors;
    private int exitCode;
    private long executionTimeMs;
    private String message;
    private String previewPath;
    private String workspaceId;
}
