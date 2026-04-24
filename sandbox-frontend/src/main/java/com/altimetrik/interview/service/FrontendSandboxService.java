package com.altimetrik.interview.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.altimetrik.interview.dto.BuildRequest;
import com.altimetrik.interview.dto.BuildResponse;
import com.altimetrik.interview.dto.CreateWorkspaceRequest;
import com.altimetrik.interview.dto.PatchWorkspaceFilesRequest;
import com.altimetrik.interview.dto.WorkspaceResponse;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.runner.FrontendRunner;
import com.altimetrik.interview.runner.PersistentFrontendRunner;
import com.altimetrik.interview.runner.model.FrontendBuildResult;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FrontendSandboxService {

    private static final long REACT_LIVE_PREVIEW_FAILURE_SETTLE_MS = 200L;

    private final List<FrontendRunner> runners;
    private final WorkspaceRegistryService workspaceRegistryService;
    private final ExecutorService watchLogExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "frontend-workspace-watch");
        thread.setDaemon(true);
        return thread;
    });

    public BuildResponse build(BuildRequest request) {
        FrontendRunner runner = resolveRunner(request.getLanguage());
        long timeoutMs = request.getTimeoutMs() > 0 ? request.getTimeoutMs() : runner.defaultTimeoutMs();
        log.info("Frontend build start language={} sessionId={} workspaceId={} fileCount={} timeoutMs={}",
                request.getLanguage(),
                request.getSessionId(),
                request.getWorkspaceId(),
                request.getFiles() == null ? 0 : request.getFiles().size(),
                timeoutMs);
        FrontendBuildResult result = buildWithWorkspaceReuse(request, runner, timeoutMs);
        String previewPath = resolvePreviewPath(request, result);
        String workspaceId = resolveWorkspaceId(request);
        log.info("Frontend build result language={} sessionId={} workspaceId={} success={} exitCode={} executionTimeMs={} stdoutLength={} stderrLength={} compileErrorCount={} rawPreviewPath={} resolvedPreviewPath={} message={}",
                request.getLanguage(),
                request.getSessionId(),
                workspaceId,
                result.isSuccess(),
                result.getExitCode(),
                result.getExecutionTimeMs(),
                result.getStdout() == null ? 0 : result.getStdout().length(),
                result.getStderr() == null ? 0 : result.getStderr().length(),
                result.getCompileErrors() == null ? 0 : result.getCompileErrors().size(),
                result.getPreviewPath(),
                previewPath,
                buildMessage(result));

        return BuildResponse.builder()
                .success(result.isSuccess())
                .stdout(result.getStdout())
                .stderr(result.getStderr())
                .compileErrors(result.getCompileErrors())
                .exitCode(result.getExitCode())
                .executionTimeMs(result.getExecutionTimeMs())
                .message(buildMessage(result))
                .previewPath(previewPath)
                .workspaceId(workspaceId)
                .build();
    }

    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request) {
        FrontendRunner runner = resolveRunner(request.getLanguage());
        if (runner instanceof PersistentFrontendRunner persistentRunner) {
            try {
                return workspaceRegistryService.withSessionLock(request.getSessionId(), () -> {
                    WorkspaceResponse existing = workspaceRegistryService.findBySessionId(request.getSessionId()).orElse(null);
                    if (existing != null) {
                        log.info("Frontend workspace create reuse language={} sessionId={} workspaceId={}",
                                request.getLanguage(), request.getSessionId(), existing.getWorkspaceId());
                        return existing;
                    }

                    Path workspaceRoot = persistentRunner.createWorkspace(request.getFiles());
                    Process process = persistentRunner.startWatchProcess(workspaceRoot);
                    Path previewRoot = persistentRunner.resolveWorkspacePreviewRoot(workspaceRoot);
                    WorkspaceResponse workspace = workspaceRegistryService.createOrGet(
                            request.getSessionId(),
                            request.getLanguage(),
                            workspaceRoot,
                            previewRoot,
                            process
                    );
                    log.info("Frontend workspace created language={} sessionId={} workspaceId={} workspaceRoot={} previewRoot={} watcherAlive={}",
                            request.getLanguage(),
                            request.getSessionId(),
                            workspace.getWorkspaceId(),
                            workspaceRoot,
                            previewRoot,
                            process.isAlive());
                    startWatchLogCapture(workspace.getWorkspaceId(), process.getInputStream(), false);
                    startWatchLogCapture(workspace.getWorkspaceId(), process.getErrorStream(), true);
                    return workspace;
                });
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to create persistent frontend workspace for session " + request.getSessionId(), exception);
            }
        }

        throw new IllegalArgumentException("No persistent workspace strategy configured for language " + request.getLanguage());
    }

    public Optional<WorkspaceResponse> findWorkspaceBySessionId(String sessionId) {
        return workspaceRegistryService.findBySessionId(sessionId);
    }

    public Optional<WorkspaceResponse> findWorkspaceById(String workspaceId) {
        return workspaceRegistryService.findByWorkspaceId(workspaceId);
    }

    public WorkspaceResponse getWorkspaceById(String workspaceId) {
        return findWorkspaceById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
    }

    public WorkspaceResponse deleteWorkspace(String workspaceId) {
        WorkspaceResponse workspace = getWorkspaceById(workspaceId);
        FrontendRunner runner = resolveRunner(workspace.getLanguage());
        try {
            return workspaceRegistryService.withWorkspaceLock(workspaceId, () -> {
                workspaceRegistryService.resolveWorkspaceRoot(workspaceId).ifPresent(workspaceRoot -> {
                    if (runner instanceof PersistentFrontendRunner persistentRunner) {
                        persistentRunner.destroyWorkspace(workspaceRoot);
                    }
                });
                return workspaceRegistryService.delete(workspaceId)
                        .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to delete frontend workspace " + workspaceId, exception);
        }
    }

    public WorkspaceResponse patchWorkspaceFiles(String workspaceId, PatchWorkspaceFilesRequest request) {
        WorkspaceResponse workspace = getWorkspaceById(workspaceId);
        FrontendRunner runner = resolveRunner(workspace.getLanguage());
        Path workspaceRoot = workspaceRegistryService.resolveWorkspaceRoot(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace root not found for " + workspaceId));
        log.info("Frontend workspace patch start language={} workspaceId={} fileCount={} workspaceRoot={}",
                workspace.getLanguage(),
                workspaceId,
                request == null || request.getFiles() == null ? 0 : request.getFiles().size(),
                workspaceRoot);

        if (runner instanceof PersistentFrontendRunner persistentRunner) {
            try {
                return workspaceRegistryService.withWorkspaceLock(workspaceId, () -> {
                    persistentRunner.patchWorkspaceFiles(workspaceRoot, request == null ? null : request.getFiles());
                    log.info("Frontend workspace patch complete language={} workspaceId={} fileCount={}",
                            workspace.getLanguage(),
                            workspaceId,
                            request == null || request.getFiles() == null ? 0 : request.getFiles().size());
                    return workspaceRegistryService.findByWorkspaceId(workspaceId).orElse(workspace);
                });
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to patch frontend workspace " + workspaceId, exception);
            }
        }

        throw new IllegalArgumentException("No workspace patch strategy configured for language " + workspace.getLanguage());
    }

    private FrontendRunner resolveRunner(ExecutionLanguage language) {
        ExecutionLanguage effectiveLanguage = language == null ? ExecutionLanguage.ANGULAR : language;
        return runners.stream()
                .filter(runner -> runner.supports(effectiveLanguage))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No frontend runner configured for language " + effectiveLanguage));
    }

    private String buildMessage(FrontendBuildResult result) {
        if (!result.getCompileErrors().isEmpty()) {
            return "Build failed";
        }
        if (!result.isSuccess()) {
            return result.getErrorMessage();
        }
        if (result.getExitCode() != 0) {
            return "Build completed with exit code: " + result.getExitCode();
        }
        return "Build successful";
    }

    private String resolvePreviewPath(BuildRequest request, FrontendBuildResult result) {
        if (result.getPreviewPath() == null || result.getPreviewPath().isBlank()) {
            return null;
        }
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            return result.getPreviewPath();
        }

        return workspaceRegistryService.registerPreview(request.getSessionId(), extractPreviewId(result.getPreviewPath()))
                .map(workspaceId -> "/workspaces/" + workspaceId + "/preview/")
                .orElse(result.getPreviewPath());
    }

    private String resolveWorkspaceId(BuildRequest request) {
        if (request.getWorkspaceId() != null && !request.getWorkspaceId().isBlank()) {
            return request.getWorkspaceId();
        }
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            return null;
        }
        return workspaceRegistryService.findBySessionId(request.getSessionId())
                .map(WorkspaceResponse::getWorkspaceId)
                .orElse(null);
    }

    private String extractPreviewId(String previewPath) {
        String normalized = previewPath == null ? "" : previewPath.trim();
        int previewIndex = normalized.indexOf("/preview/");
        if (previewIndex < 0) {
            return normalized;
        }
        String suffix = normalized.substring(previewIndex + "/preview/".length());
        int nextSlash = suffix.indexOf('/');
        return nextSlash >= 0 ? suffix.substring(0, nextSlash) : suffix;
    }

    private FrontendBuildResult buildWithWorkspaceReuse(BuildRequest request, FrontendRunner runner, long timeoutMs) {
        if (runner instanceof PersistentFrontendRunner persistentRunner) {
            String workspaceId = resolveWorkspaceId(request);
            Path workspaceRoot = resolveWorkspaceRoot(request).orElse(null);
            if (workspaceRoot != null && workspaceId != null && !workspaceId.isBlank()) {
                try {
                    return workspaceRegistryService.withWorkspaceLock(workspaceId, () -> {
                        log.info("Frontend build using persistent workspace language={} sessionId={} workspaceId={} workspaceRoot={}",
                                request.getLanguage(), request.getSessionId(), workspaceId, workspaceRoot);
                        boolean watcherAlive = workspaceRegistryService.resolveProcess(workspaceId)
                                .map(Process::isAlive)
                                .orElse(false);
                        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
                            persistentRunner.patchWorkspaceFiles(workspaceRoot, request.getFiles());
                        }
                        if (watcherAlive) {
                            workspaceRegistryService.markBuildRequested(workspaceId);
                        }
                        if (watcherAlive) {
                            FrontendBuildResult watcherResult = awaitWatchBuild(
                                    workspaceId,
                                    resolveWatchBuildTimeoutMs(request.getLanguage(), timeoutMs)
                            );
                            if (isReactLivePreviewMode(request)) {
                                if (watcherResult != null) {
                                    if (!watcherResult.isSuccess()) {
                                        return settleReactLivePreviewFailure(workspaceId, watcherResult);
                                    }
                                    return watcherResult;
                                }
                                log.info("React live preview build still updating for workspaceId={}, returning current warm preview", workspaceId);
                                return FrontendBuildResult.success(
                                        "React live preview is updating.",
                                        "",
                                        0,
                                        resolveWatchBuildTimeoutMs(request.getLanguage(), timeoutMs),
                                        "/workspaces/" + workspaceId + "/preview/"
                                );
                            }
                            if (watcherResult != null) {
                                if (!watcherResult.isSuccess() && hasOnlyGenericWatchFailure(watcherResult)) {
                                    log.warn("Frontend watcher build produced only generic failure output for workspaceId={}, running direct build for detailed diagnostics", workspaceId);
                                    return persistentRunner.buildInWorkspace(workspaceRoot, timeoutMs);
                                }
                                return watcherResult;
                            }
                            log.warn("Frontend watcher build did not resolve in time for workspaceId={}, falling back to direct build", workspaceId);
                        }
                        return persistentRunner.buildInWorkspace(workspaceRoot, timeoutMs);
                    });
                } catch (Exception exception) {
                    log.warn("Frontend build lock failed for workspaceId={}", workspaceId, exception);
                    return FrontendBuildResult.error(exception.getMessage());
                }
            }
            log.info("Frontend build has no persistent workspace language={} sessionId={} workspaceId={}, falling back to cold build",
                    request.getLanguage(), request.getSessionId(), request.getWorkspaceId());
        }
        return runner.build(request.getFiles(), timeoutMs);
    }

    private boolean isReactLivePreviewMode(BuildRequest request) {
        return request.isLivePreviewMode() && request.getLanguage() == ExecutionLanguage.REACT;
    }

    private FrontendBuildResult settleReactLivePreviewFailure(String workspaceId, FrontendBuildResult initialResult) {
        try {
            TimeUnit.MILLISECONDS.sleep(REACT_LIVE_PREVIEW_FAILURE_SETTLE_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return initialResult;
        }

        FrontendBuildResult settledResult = awaitWatchBuild(workspaceId, 1L);
        return settledResult == null ? initialResult : settledResult;
    }

    private FrontendBuildResult awaitWatchBuild(String workspaceId, long timeoutMs) {
        WorkspaceRegistryService.WorkspaceBuildState buildState = workspaceRegistryService.awaitBuildCompletion(workspaceId, timeoutMs)
                .orElse(null);
        if (buildState == null || buildState.getLastBuildRequestedAt() == null || buildState.getLastBuildCompletedAt() == null) {
            return null;
        }
        if (buildState.getLastBuildCompletedAt().isBefore(buildState.getLastBuildRequestedAt())) {
            return null;
        }

        long executionTimeMs = Math.max(0L, Duration.between(buildState.getLastBuildRequestedAt(), buildState.getLastBuildCompletedAt()).toMillis());
        if (buildState.isSucceeded()) {
            return FrontendBuildResult.success(
                    buildState.getStdout(),
                    buildState.getStderr(),
                    0,
                    executionTimeMs,
                    "/workspaces/" + workspaceId + "/preview/"
            );
        }

        List<String> compileErrors = extractBuildErrors(buildState.getStdout(), buildState.getStderr());
        return FrontendBuildResult.compilationFailed(
                compileErrors,
                buildState.getStdout(),
                buildState.getStderr(),
                -1,
                executionTimeMs
        );
    }

    private List<String> extractBuildErrors(String stdout, String stderr) {
        List<String> lines = new ArrayList<>();
        if (stderr != null && !stderr.isBlank()) {
            stderr.lines().map(String::trim).filter(line -> !line.isBlank()).forEach(lines::add);
        }
        if (lines.isEmpty() && stdout != null && !stdout.isBlank()) {
            stdout.lines().map(String::trim).filter(line -> !line.isBlank()).forEach(lines::add);
        }
        return lines.isEmpty() ? List.of("Build failed") : lines;
    }

    private boolean hasOnlyGenericWatchFailure(FrontendBuildResult result) {
        List<String> lines = extractBuildErrors(result.getStdout(), result.getStderr());
        if (lines.isEmpty()) {
            return true;
        }

        return lines.stream().allMatch(this::isGenericWatchFailureLine);
    }

    private boolean isGenericWatchFailureLine(String line) {
        String normalized = line == null ? "" : line.trim().toLowerCase();
        if (normalized.isBlank()) {
            return true;
        }

        return normalized.startsWith("changes detected. rebuilding")
                || normalized.startsWith("❯ changes detected. rebuilding")
                || normalized.startsWith("✔ changes detected. rebuilding")
                || normalized.startsWith("vite v")
                || normalized.startsWith("transforming")
                || normalized.startsWith("rendering chunks")
                || normalized.startsWith("computing gzip size")
                || normalized.startsWith("built in ")
                || normalized.startsWith("application bundle generation failed")
                || normalized.startsWith("application bundle generation complete")
                || normalized.startsWith("watch stream closed:");
    }

    private long resolveWatchBuildTimeoutMs(ExecutionLanguage language, long timeoutMs) {
        long boundedTimeoutMs = timeoutMs > 0 ? timeoutMs : 1_000L;
        long preferredTimeoutMs = language == ExecutionLanguage.REACT ? 700L : 2_000L;
        return Math.min(boundedTimeoutMs, preferredTimeoutMs);
    }

    private java.util.Optional<Path> resolveWorkspaceRoot(BuildRequest request) {
        if (request.getWorkspaceId() != null && !request.getWorkspaceId().isBlank()) {
            return workspaceRegistryService.resolveWorkspaceRoot(request.getWorkspaceId());
        }
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            return workspaceRegistryService.resolveWorkspaceRootBySessionId(request.getSessionId());
        }
        return java.util.Optional.empty();
    }

    private void startWatchLogCapture(String workspaceId, InputStream stream, boolean stderr) {
        watchLogExecutor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    workspaceRegistryService.recordWatchOutput(workspaceId, line, stderr);
                }
            } catch (Exception exception) {
                workspaceRegistryService.recordWatchOutput(workspaceId, "Watch stream closed: " + exception.getMessage(), stderr);
            }
        });
    }

    @PreDestroy
    void shutdownWatchLogExecutor() {
        watchLogExecutor.shutdown();
        try {
            if (!watchLogExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                watchLogExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            watchLogExecutor.shutdownNow();
        }
    }
}
