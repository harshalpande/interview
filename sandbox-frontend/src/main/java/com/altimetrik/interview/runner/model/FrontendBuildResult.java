package com.altimetrik.interview.runner.model;

import java.util.Collections;
import java.util.List;

public class FrontendBuildResult {
    private final boolean success;
    private final String stdout;
    private final String stderr;
    private final List<String> compileErrors;
    private final int exitCode;
    private final long executionTimeMs;
    private final String errorMessage;
    private final String previewPath;

    private FrontendBuildResult(boolean success,
                                String stdout,
                                String stderr,
                                List<String> compileErrors,
                                int exitCode,
                                long executionTimeMs,
                                String errorMessage,
                                String previewPath) {
        this.success = success;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.compileErrors = compileErrors == null ? Collections.emptyList() : compileErrors;
        this.exitCode = exitCode;
        this.executionTimeMs = executionTimeMs;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
        this.previewPath = previewPath;
    }

    public static FrontendBuildResult success(String stdout, String stderr, int exitCode, long executionTimeMs, String previewPath) {
        return new FrontendBuildResult(true, stdout, stderr, Collections.emptyList(), exitCode, executionTimeMs, "", previewPath);
    }

    public static FrontendBuildResult compilationFailed(List<String> errors, String stdout, String stderr, int exitCode, long executionTimeMs) {
        return new FrontendBuildResult(false, stdout, stderr, errors, exitCode, executionTimeMs, "Build failed", null);
    }

    public static FrontendBuildResult timeout(long timeoutMs) {
        return new FrontendBuildResult(false, "", "", Collections.emptyList(), -1, timeoutMs, "Build timeout", null);
    }

    public static FrontendBuildResult error(String message) {
        return new FrontendBuildResult(false, "", "", Collections.emptyList(), -1, 0, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public List<String> getCompileErrors() {
        return compileErrors;
    }

    public int getExitCode() {
        return exitCode;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getPreviewPath() {
        return previewPath;
    }
}
