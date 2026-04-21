package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.CompileRequest;
import com.altimetrik.interview.dto.CompileResponse;
import com.altimetrik.interview.dto.ExecuteRequest;
import com.altimetrik.interview.dto.ExecuteResponse;
import com.altimetrik.interview.dto.FrontendBuildRequest;
import com.altimetrik.interview.dto.FrontendBuildResponse;
import com.altimetrik.interview.dto.FrontendWorkspaceResponse;
import com.altimetrik.interview.enums.ExecutionLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxClientService {

    @Qualifier("sandboxRestClient")
    private final RestClient sandboxRestClient;
    private final FrontendSandboxClientService frontendSandboxClientService;
    @Value("${sandbox.frontend-public-base-url:http://localhost:8083/api}")
    private String sandboxFrontendPublicBaseUrl;

    public CompileResponse compile(CompileRequest request) {
        return post("/compile", request, CompileResponse.class);
    }

    public ExecuteResponse execute(ExecuteRequest request) {
        if (isFrontendLanguage(request.getLanguage())) {
            log.info("Frontend execute request language={} sessionId={} codeFileCount={} timeoutMs={} memoryLimitMb={}",
                    request.getLanguage(),
                    request.getSessionId(),
                    request.getCodeFiles() == null ? 0 : request.getCodeFiles().size(),
                    request.getTimeoutMs(),
                    request.getMemoryLimitMb());
            FrontendWorkspaceResponse workspace = resolveFrontendWorkspace(request.getSessionId(), request.getLanguage(), request.getCodeFiles());
            log.info("Frontend workspace resolved language={} sessionId={} workspaceId={} previewPath={}",
                    request.getLanguage(),
                    request.getSessionId(),
                    workspace == null ? null : workspace.getWorkspaceId(),
                    workspace == null ? null : workspace.getPreviewPath());
            log.info("Frontend build dispatch language={} sessionId={} workspaceId={} fallbackFileCount={}",
                    request.getLanguage(),
                    request.getSessionId(),
                    workspace == null ? null : workspace.getWorkspaceId(),
                    workspace == null || request.getCodeFiles() == null ? 0 : request.getCodeFiles().size());
            FrontendBuildResponse response = frontendSandboxClientService.build(FrontendBuildRequest.builder()
                    .sessionId(request.getSessionId())
                    .workspaceId(workspace == null ? null : workspace.getWorkspaceId())
                    .language(request.getLanguage())
                    .files(request.getCodeFiles())
                    .timeoutMs(request.getTimeoutMs())
                    .build());
            String previewUrl = resolveFrontendPreviewUrl(response.getPreviewPath());
            log.info("Frontend sandbox execution completed language={} sessionId={} success={} exitCode={} executionTimeMs={} previewPath={} previewUrl={} stderrLength={} compileErrorCount={} message={}",
                    request.getLanguage(),
                    request.getSessionId(),
                    response.isSuccess(),
                    response.getExitCode(),
                    response.getExecutionTimeMs(),
                    response.getPreviewPath(),
                    previewUrl,
                    response.getStderr() == null ? 0 : response.getStderr().length(),
                    response.getCompileErrors() == null ? 0 : response.getCompileErrors().size(),
                    response.getMessage());
            return ExecuteResponse.builder()
                    .success(response.isSuccess())
                    .stdout(response.getStdout())
                    .stderr(response.getStderr())
                    .compileErrors(response.getCompileErrors())
                    .exitCode(response.getExitCode())
                    .executionTimeMs(response.getExecutionTimeMs())
                    .message(response.getMessage())
                    .previewUrl(previewUrl)
                    .build();
        }
        return post("/compile/run", request, ExecuteResponse.class);
    }

    private FrontendWorkspaceResponse resolveFrontendWorkspace(String sessionId,
                                                               ExecutionLanguage language,
                                                               java.util.List<com.altimetrik.interview.dto.EditableCodeFileDto> codeFiles) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        FrontendWorkspaceResponse response = frontendSandboxClientService.getWorkspaceBySessionId(sessionId);
        if (response != null) {
            return response;
        }
        try {
            return frontendSandboxClientService.createWorkspace(com.altimetrik.interview.dto.FrontendWorkspaceRequest.builder()
                    .sessionId(sessionId)
                    .language(language)
                    .files(codeFiles)
                    .build());
        } catch (ResponseStatusException exception) {
            log.warn("Frontend workspace lookup failed for language={} session {}. Falling back to cold-build flow.", language, sessionId, exception);
            return null;
        }
    }

    private boolean isFrontendLanguage(ExecutionLanguage language) {
        return language == ExecutionLanguage.ANGULAR || language == ExecutionLanguage.REACT;
    }

    private String resolveFrontendPreviewUrl(String previewPath) {
        if (previewPath == null || previewPath.isBlank()) {
            return null;
        }
        String baseUrl = sandboxFrontendPublicBaseUrl == null ? "" : sandboxFrontendPublicBaseUrl.trim();
        if (baseUrl.endsWith("/") && previewPath.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + previewPath;
        }
        if (!baseUrl.endsWith("/") && !previewPath.startsWith("/")) {
            return baseUrl + "/" + previewPath;
        }
        return baseUrl + previewPath;
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        try {
            return Objects.requireNonNull(sandboxRestClient.post()
                    .uri(path)
                    .body(request)
                    .retrieve()
                    .body(responseType), "Sandbox response was empty for " + path);
        } catch (RestClientException ex) {
            log.error("Sandbox request failed for {}", path, ex);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Sandbox service is unavailable");
        }
    }
}
