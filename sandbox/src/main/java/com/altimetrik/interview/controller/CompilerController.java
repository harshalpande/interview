package com.altimetrik.interview.controller;

import com.altimetrik.interview.dto.CompileRequest;
import com.altimetrik.interview.dto.CompileResponse;
import com.altimetrik.interview.dto.ExecuteRequest;
import com.altimetrik.interview.dto.ExecuteResponse;
import com.altimetrik.interview.service.JavaCompilerService;
import com.altimetrik.interview.service.JavaCompilerService.CompileResult;
import com.altimetrik.interview.service.JavaCompilerService.ExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/compile")
@RequiredArgsConstructor
@Slf4j
public class CompilerController {

    private final JavaCompilerService compilerService;

    @PostMapping
    public ResponseEntity<CompileResponse> compile(@RequestBody CompileRequest request) {
        log.info("Received compile request");

        if (request.getSourceCode() == null || request.getSourceCode().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(CompileResponse.builder()
                            .success(false)
                            .message("Source code cannot be empty")
                            .build());
        }

        CompileResult result = compilerService.compile(request.getSourceCode());

        CompileResponse response = CompileResponse.builder()
                .success(result.isSuccess())
                .compileErrors(result.getErrors())
                .message(result.isSuccess() ? "Compilation successful" : "Compilation failed")
                .build();

        log.info("Compile response: success={}", result.isSuccess());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/run")
    public ResponseEntity<ExecuteResponse> execute(@RequestBody ExecuteRequest request) {
        log.info("Received execute request with timeout={}ms, memory={}MB",
                request.getTimeoutMs(), request.getMemoryLimitMb());

        if (request.getSourceCode() == null || request.getSourceCode().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ExecuteResponse.builder()
                            .success(false)
                            .message("Source code cannot be empty")
                            .build());
        }

        long timeoutMs = request.getTimeoutMs() > 0
                ? request.getTimeoutMs()
                : JavaCompilerService.defaultTimeoutMs();
        long memoryMb = request.getMemoryLimitMb() > 0
                ? Math.min(request.getMemoryLimitMb(), JavaCompilerService.maxMemoryMb())
                : JavaCompilerService.defaultMemoryMb();

        ExecutionResult result = compilerService.execute(request.getSourceCode(), timeoutMs, memoryMb);

        ExecuteResponse response = ExecuteResponse.builder()
                .success(result.isSuccess())
                .stdout(result.getStdout())
                .stderr(result.getStderr())
                .compileErrors(result.getCompileErrors())
                .exitCode(result.getExitCode())
                .executionTimeMs(result.getExecutionTimeMs())
                .message(buildMessage(result))
                .build();

        log.info("Execute response: success={}, exitCode={}, executionTime={}ms",
                result.isSuccess(), result.getExitCode(), result.getExecutionTimeMs());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute")
    public ResponseEntity<ExecuteResponse> executeAlias(@RequestBody ExecuteRequest request) {
        return execute(request);
    }

    private String buildMessage(ExecutionResult result) {
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
