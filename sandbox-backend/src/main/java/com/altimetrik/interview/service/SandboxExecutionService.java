package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.CompileRequest;
import com.altimetrik.interview.dto.CompileResponse;
import com.altimetrik.interview.dto.ExecuteRequest;
import com.altimetrik.interview.dto.ExecuteResponse;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.runner.LanguageRunner;
import com.altimetrik.interview.runner.model.RunnerCompileResult;
import com.altimetrik.interview.runner.model.RunnerExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SandboxExecutionService {

    private final List<LanguageRunner> runners;

    public CompileResponse compile(CompileRequest request) {
        LanguageRunner runner = resolveRunner(request.getLanguage());
        RunnerCompileResult result = runner.compile(request.getSourceCode());
        return CompileResponse.builder()
                .success(result.isSuccess())
                .compileErrors(result.getErrors())
                .message(result.isSuccess() ? "Compilation successful" : "Compilation failed")
                .build();
    }

    public ExecuteResponse execute(ExecuteRequest request) {
        LanguageRunner runner = resolveRunner(request.getLanguage());
        long timeoutMs = request.getTimeoutMs() > 0
                ? request.getTimeoutMs()
                : runner.defaultTimeoutMs();
        long memoryMb = request.getMemoryLimitMb() > 0
                ? Math.min(request.getMemoryLimitMb(), runner.maxMemoryMb())
                : runner.defaultMemoryMb();

        RunnerExecutionResult result = runner.execute(request.getSourceCode(), timeoutMs, memoryMb);
        return ExecuteResponse.builder()
                .success(result.isSuccess())
                .stdout(result.getStdout())
                .stderr(result.getStderr())
                .compileErrors(result.getCompileErrors())
                .exitCode(result.getExitCode())
                .executionTimeMs(result.getExecutionTimeMs())
                .message(buildMessage(result))
                .build();
    }

    private LanguageRunner resolveRunner(ExecutionLanguage language) {
        ExecutionLanguage effectiveLanguage = language == null ? ExecutionLanguage.JAVA : language;
        return runners.stream()
                .filter(runner -> runner.supports(effectiveLanguage))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No runner configured for language " + effectiveLanguage));
    }

    private String buildMessage(RunnerExecutionResult result) {
        if (!result.getCompileErrors().isEmpty()) {
            return "Compilation failed";
        }
        if (!result.isSuccess()) {
            return result.getErrorMessage();
        }
        if (result.getExitCode() != 0) {
            return "Execution completed with exit code: " + result.getExitCode();
        }
        return "Execution successful";
    }
}
