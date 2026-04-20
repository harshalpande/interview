package com.altimetrik.interview.runner.model;

import java.util.Collections;
import java.util.List;

public class RunnerExecutionResult {
    private final boolean success;
    private final String stdout;
    private final String stderr;
    private final List<String> compileErrors;
    private final int exitCode;
    private final long executionTimeMs;
    private final String errorMessage;

    private RunnerExecutionResult(boolean success,
                                  String stdout,
                                  String stderr,
                                  List<String> compileErrors,
                                  int exitCode,
                                  long executionTimeMs,
                                  String errorMessage) {
        this.success = success;
        this.stdout = stdout != null ? stdout : "";
        this.stderr = stderr != null ? stderr : "";
        this.compileErrors = compileErrors != null ? compileErrors : Collections.emptyList();
        this.exitCode = exitCode;
        this.executionTimeMs = executionTimeMs;
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    public static RunnerExecutionResult success(String stdout, String stderr, int exitCode, long executionTimeMs) {
        return new RunnerExecutionResult(true, stdout, stderr, Collections.emptyList(), exitCode, executionTimeMs, "");
    }

    public static RunnerExecutionResult compilationFailed(List<String> errors) {
        return new RunnerExecutionResult(false, "", "", errors, -1, 0, "Compilation failed");
    }

    public static RunnerExecutionResult timeout(long timeoutMs) {
        return new RunnerExecutionResult(false, "", "", Collections.emptyList(), -1, timeoutMs, "Execution timeout");
    }

    public static RunnerExecutionResult error(String message) {
        return new RunnerExecutionResult(false, "", "", Collections.emptyList(), -1, 0, message);
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
}
