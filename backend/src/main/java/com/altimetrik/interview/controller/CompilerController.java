package com.altimetrik.interview.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.altimetrik.interview.dto.CompileRequest;
import com.altimetrik.interview.dto.CompileResponse;
import com.altimetrik.interview.dto.ExecuteRequest;
import com.altimetrik.interview.dto.ExecuteResponse;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.service.SandboxClientService;
import com.altimetrik.interview.service.SessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for sandboxed code compilation and execution endpoints.
 */
@RestController
@RequestMapping("/compile")
@RequiredArgsConstructor
@Slf4j
public class CompilerController {

    private final SandboxClientService sandboxClientService;
    private final SessionService sessionService;

    /**
     * POST /api/compile
     * Compiles source code without execution.
     * 
     * @param request CompileRequest containing source code
     * @return CompileResponse with compilation status and errors (if any)
     */
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

        CompileResponse response = sandboxClientService.compile(request);
        log.info("Compile response: success={}, language={}", response.isSuccess(), request.getLanguage());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/execute
     * Compiles and executes source code in a sandboxed environment.
     * 
     * @param request ExecuteRequest containing source code and execution constraints
     * @return ExecuteResponse with execution results, output, and errors
     */
    @PostMapping("/run")
    public ResponseEntity<ExecuteResponse> execute(@RequestBody ExecuteRequest request) {
        log.info("Received execute request with timeout={}ms, memory={}MB", 
                 request.getTimeoutMs(), request.getMemoryLimitMb());
        
        if (!hasExecutablePayload(request)) {
            return ResponseEntity.badRequest()
                    .body(ExecuteResponse.builder()
                            .success(false)
                            .message("Source code or editable files are required")
                            .build());
        }

        ExecuteResponse response = sandboxClientService.execute(request);
        sessionService.recordQuestionRunResult(request, response);
        log.info("Execute response: success={}, exitCode={}, executionTime={}ms, language={}",
                response.isSuccess(), response.getExitCode(), response.getExecutionTimeMs(), request.getLanguage());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/execute (alternative single endpoint for compile+execute)
     * This is an alias for the /run endpoint.
     */
    @PostMapping("/execute")
    public ResponseEntity<ExecuteResponse> executeAlias(@RequestBody ExecuteRequest request) {
        return execute(request);
    }

    private boolean hasExecutablePayload(ExecuteRequest request) {
        if (request.getLanguage() == ExecutionLanguage.ANGULAR || request.getLanguage() == ExecutionLanguage.REACT) {
            return request.getCodeFiles() != null && !request.getCodeFiles().isEmpty();
        }
        return request.getSourceCode() != null && !request.getSourceCode().trim().isEmpty();
    }

}
