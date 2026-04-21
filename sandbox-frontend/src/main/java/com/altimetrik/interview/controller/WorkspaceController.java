package com.altimetrik.interview.controller;

import com.altimetrik.interview.dto.BuildRequest;
import com.altimetrik.interview.dto.BuildResponse;
import com.altimetrik.interview.dto.CreateWorkspaceRequest;
import com.altimetrik.interview.dto.PatchWorkspaceFilesRequest;
import com.altimetrik.interview.dto.WorkspaceResponse;
import com.altimetrik.interview.service.FrontendSandboxService;
import com.altimetrik.interview.service.PreviewStorageService;
import com.altimetrik.interview.service.WorkspaceRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.io.IOException;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
public class WorkspaceController {

    private final FrontendSandboxService frontendSandboxService;
    private final PreviewStorageService previewStorageService;
    private final WorkspaceRegistryService workspaceRegistryService;

    @PostMapping("/workspace/build")
    public ResponseEntity<BuildResponse> build(@RequestBody BuildRequest request) {
        log.info("Received frontend build request for {}", request.getLanguage());
        boolean hasFiles = request.getFiles() != null && !request.getFiles().isEmpty();
        boolean hasWorkspaceReference = (request.getWorkspaceId() != null && !request.getWorkspaceId().isBlank())
                || (request.getSessionId() != null && !request.getSessionId().isBlank());
        if (!hasFiles && !hasWorkspaceReference) {
            return ResponseEntity.badRequest()
                    .body(BuildResponse.builder()
                            .success(false)
                            .message("Workspace reference or editable files are required")
                            .build());
        }

        BuildResponse response = frontendSandboxService.build(request);
        log.info("Frontend build response: success={}, exitCode={}, executionTime={}ms, language={}",
                response.isSuccess(), response.getExitCode(), response.getExecutionTimeMs(), request.getLanguage());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/workspaces")
    public ResponseEntity<WorkspaceResponse> createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(frontendSandboxService.createWorkspace(request));
    }

    @GetMapping("/workspaces/session/{sessionId}")
    public ResponseEntity<WorkspaceResponse> getWorkspaceBySessionId(@PathVariable String sessionId) {
        WorkspaceResponse workspace = frontendSandboxService.findWorkspaceBySessionId(sessionId).orElse(null);
        return workspace == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(workspace);
    }

    @GetMapping("/workspaces/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getWorkspace(@PathVariable String workspaceId) {
        WorkspaceResponse workspace = frontendSandboxService.findWorkspaceById(workspaceId).orElse(null);
        return workspace == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(workspace);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/workspaces/{workspaceId}/files")
    public ResponseEntity<WorkspaceResponse> patchWorkspaceFiles(@PathVariable String workspaceId,
                                                                @RequestBody PatchWorkspaceFilesRequest request) {
        return ResponseEntity.ok(frontendSandboxService.patchWorkspaceFiles(workspaceId, request));
    }

    @DeleteMapping("/workspaces/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> deleteWorkspace(@PathVariable String workspaceId) {
        return ResponseEntity.ok(frontendSandboxService.deleteWorkspace(workspaceId));
    }

    @GetMapping("/workspace/preview/{previewId}/archive")
    public ResponseEntity<byte[]> previewArchive(@PathVariable String previewId) throws IOException {
        byte[] archive = previewStorageService.createPreviewArchive(previewId);
        if (archive == null || archive.length == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + previewId + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(archive);
    }

    @GetMapping("/workspaces/{workspaceId}/preview/archive")
    public ResponseEntity<byte[]> workspacePreviewArchive(@PathVariable String workspaceId) throws IOException {
        java.nio.file.Path previewRoot = workspaceRegistryService.resolvePreviewRoot(workspaceId).orElse(null);
        byte[] archive = previewStorageService.createLivePreviewArchive(previewRoot);
        if (archive == null || archive.length == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + workspaceId + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(archive);
    }

    @GetMapping({"/workspace/preview/{previewId}", "/workspace/preview/{previewId}/", "/workspace/preview/{previewId}/{*assetPath}"})
    public ResponseEntity<Resource> preview(@PathVariable String previewId,
                                            @PathVariable(required = false) String assetPath) throws IOException {
        String normalizedAssetPath = assetPath == null ? "" : assetPath.replaceFirst("^/", "");
        Resource resource = previewStorageService.resolvePreviewResource(previewId, normalizedAssetPath);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = previewStorageService.detectContentType(resource);
        MediaType mediaType = contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(resource);
    }

    @GetMapping({"/workspaces/{workspaceId}/preview", "/workspaces/{workspaceId}/preview/", "/workspaces/{workspaceId}/preview/{*assetPath}"})
    public ResponseEntity<Resource> workspacePreview(@PathVariable String workspaceId,
                                                     @PathVariable(required = false) String assetPath) throws IOException {
        String normalizedAssetPath = assetPath == null ? "" : assetPath.replaceFirst("^/", "");
        java.nio.file.Path previewRoot = workspaceRegistryService.resolvePreviewRoot(workspaceId).orElse(null);
        if (previewRoot == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = previewStorageService.resolveLivePreviewResource(previewRoot, normalizedAssetPath);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = previewStorageService.detectContentType(resource);
        MediaType mediaType = contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(resource);
    }
}
