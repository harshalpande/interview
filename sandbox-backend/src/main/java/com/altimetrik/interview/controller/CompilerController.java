package com.altimetrik.interview.controller;

import com.altimetrik.interview.dto.CompileRequest;
import com.altimetrik.interview.dto.CompileResponse;
import com.altimetrik.interview.dto.ExecuteRequest;
import com.altimetrik.interview.dto.ExecuteResponse;
import com.altimetrik.interview.service.SandboxExecutionService;
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

    private final SandboxExecutionService sandboxExecutionService;

    @PostMapping
    public ResponseEntity<CompileResponse> compile(@RequestBody CompileRequest request) {
        log.debug("Compile request received language={}", request.getLanguage());

        if (request.getSourceCode() == null || request.getSourceCode().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(CompileResponse.builder()
                            .success(false)
                            .message("Source code cannot be empty")
                            .build());
        }

        CompileResponse response = sandboxExecutionService.compile(request);

        log.debug("Compile completed success={} language={}", response.isSuccess(), request.getLanguage());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/run")
    public ResponseEntity<ExecuteResponse> execute(@RequestBody ExecuteRequest request) {
        log.debug("Execute request received timeoutMs={} memoryLimitMb={} language={}",
                request.getTimeoutMs(), request.getMemoryLimitMb(), request.getLanguage());

        if (request.getSourceCode() == null || request.getSourceCode().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ExecuteResponse.builder()
                            .success(false)
                            .message("Source code cannot be empty")
                            .build());
        }

        ExecuteResponse response = sandboxExecutionService.execute(request);

        log.debug("Execute completed success={} exitCode={} executionTimeMs={} language={}",
                response.isSuccess(), response.getExitCode(), response.getExecutionTimeMs(), request.getLanguage());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute")
    public ResponseEntity<ExecuteResponse> executeAlias(@RequestBody ExecuteRequest request) {
        return execute(request);
    }
}
