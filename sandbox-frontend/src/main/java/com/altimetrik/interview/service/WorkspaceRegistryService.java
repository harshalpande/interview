package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.WorkspaceResponse;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.enums.WorkspaceStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;

@Service
public class WorkspaceRegistryService {

    private final Map<String, WorkspaceEntry> byWorkspaceId = new ConcurrentHashMap<>();
    private final Map<String, String> workspaceIdBySessionId = new ConcurrentHashMap<>();
    private final Map<String, Object> sessionMonitors = new ConcurrentHashMap<>();

    public synchronized WorkspaceResponse createOrGet(String sessionId,
                                                      ExecutionLanguage language,
                                                      Path workspaceRoot,
                                                      Path previewRoot,
                                                      Process process) {
        String existingWorkspaceId = workspaceIdBySessionId.get(sessionId);
        if (existingWorkspaceId != null) {
            WorkspaceEntry existing = byWorkspaceId.get(existingWorkspaceId);
            if (existing != null) {
                existing.updatedAt = nowUtc();
                existing.lastHeartbeatAt = existing.updatedAt;
                return toResponse(existing);
            }
        }

        OffsetDateTime now = nowUtc();
        WorkspaceEntry created = new WorkspaceEntry();
        created.sessionId = sessionId;
        created.workspaceId = UUID.randomUUID().toString();
        created.language = language == null ? ExecutionLanguage.ANGULAR : language;
        created.status = WorkspaceStatus.READY;
        created.previewPath = "/workspaces/" + created.workspaceId + "/preview/";
        created.workspaceRoot = workspaceRoot;
        created.previewRoot = previewRoot;
        created.process = process;
        created.createdAt = now;
        created.updatedAt = now;
        created.lastHeartbeatAt = now;
        created.lastBuildRequestedAt = now;

        byWorkspaceId.put(created.workspaceId, created);
        workspaceIdBySessionId.put(sessionId, created.workspaceId);
        return toResponse(created);
    }

    public <T> T withSessionLock(String sessionId, Callable<T> action) throws Exception {
        if (sessionId == null || sessionId.isBlank()) {
            return action.call();
        }
        Object monitor = sessionMonitors.computeIfAbsent(sessionId, ignored -> new Object());
        synchronized (monitor) {
            return action.call();
        }
    }

    public <T> T withWorkspaceLock(String workspaceId, Callable<T> action) throws Exception {
        WorkspaceEntry entry = byWorkspaceId.get(workspaceId);
        if (entry == null) {
            return action.call();
        }
        synchronized (entry.monitor) {
            return action.call();
        }
    }

    public Optional<WorkspaceResponse> findBySessionId(String sessionId) {
        String workspaceId = workspaceIdBySessionId.get(sessionId);
        if (workspaceId == null) {
            return Optional.empty();
        }
        return findByWorkspaceId(workspaceId);
    }

    public Optional<WorkspaceResponse> findByWorkspaceId(String workspaceId) {
        WorkspaceEntry entry = byWorkspaceId.get(workspaceId);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(toResponse(entry));
    }

    public Optional<WorkspaceResponse> delete(String workspaceId) {
        WorkspaceEntry removed = byWorkspaceId.remove(workspaceId);
        if (removed == null) {
            return Optional.empty();
        }
        workspaceIdBySessionId.remove(removed.sessionId);
        sessionMonitors.remove(removed.sessionId);
        destroyProcess(removed.process);
        removed.status = WorkspaceStatus.STOPPED;
        removed.updatedAt = nowUtc();
        removed.lastHeartbeatAt = removed.updatedAt;
        return Optional.of(toResponse(removed));
    }

    public Optional<Path> resolveWorkspaceRoot(String workspaceId) {
        WorkspaceEntry entry = byWorkspaceId.get(workspaceId);
        if (entry == null) {
            return Optional.empty();
        }
        entry.updatedAt = nowUtc();
        entry.lastHeartbeatAt = entry.updatedAt;
        return Optional.ofNullable(entry.workspaceRoot);
    }

    public Optional<Path> resolveWorkspaceRootBySessionId(String sessionId) {
        String workspaceId = workspaceIdBySessionId.get(sessionId);
        if (workspaceId == null) {
            return Optional.empty();
        }
        return resolveWorkspaceRoot(workspaceId);
    }

    public Optional<String> resolveWorkspaceIdBySessionId(String sessionId) {
        return Optional.ofNullable(workspaceIdBySessionId.get(sessionId));
    }

    public Optional<Path> resolvePreviewRoot(String workspaceId) {
        WorkspaceEntry entry = byWorkspaceId.get(workspaceId);
        if (entry == null) {
            return Optional.empty();
        }
        entry.updatedAt = nowUtc();
        entry.lastHeartbeatAt = entry.updatedAt;
        return Optional.ofNullable(entry.previewRoot);
    }

    public Optional<WorkspaceBuildState> markBuildRequested(String workspaceId) {
        WorkspaceEntry entry = byWorkspaceId.get(workspaceId);
        if (entry == null) {
            return Optional.empty();
        }
        synchronized (entry.monitor) {
            entry.stdoutLines.clear();
            entry.stderrLines.clear();
            entry.lastBuildSucceeded = null;
            entry.lastBuildCompletedAt = null;
            entry.lastBuildRequestedAt = nowUtc();
            entry.updatedAt = entry.lastBuildRequestedAt;
            entry.lastHeartbeatAt = entry.updatedAt;
            return Optional.of(toBuildState(entry));
        }
    }

    public Optional<WorkspaceBuildState> awaitBuildCompletion(String workspaceId, long timeoutMs) {
        WorkspaceEntry entry = byWorkspaceId.get(workspaceId);
        if (entry == null) {
            return Optional.empty();
        }
        long waitDeadline = System.currentTimeMillis() + Math.max(timeoutMs, 1_000L);
        synchronized (entry.monitor) {
            while (System.currentTimeMillis() < waitDeadline) {
                if (entry.lastBuildCompletedAt != null
                        && entry.lastBuildRequestedAt != null
                        && !entry.lastBuildCompletedAt.isBefore(entry.lastBuildRequestedAt)) {
                    entry.updatedAt = nowUtc();
                    entry.lastHeartbeatAt = entry.updatedAt;
                    return Optional.of(toBuildState(entry));
                }
                if (entry.process != null && !entry.process.isAlive()) {
                    entry.status = WorkspaceStatus.FAILED;
                    entry.updatedAt = nowUtc();
                    entry.lastHeartbeatAt = entry.updatedAt;
                    return Optional.of(toBuildState(entry));
                }
                try {
                    entry.monitor.wait(250L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        entry.updatedAt = nowUtc();
        entry.lastHeartbeatAt = entry.updatedAt;
        return Optional.of(toBuildState(entry));
    }

    public void recordWatchOutput(String workspaceId, String line, boolean stderr) {
        WorkspaceEntry entry = byWorkspaceId.get(workspaceId);
        if (entry == null || line == null) {
            return;
        }
        synchronized (entry.monitor) {
            ArrayDeque<String> target = stderr ? entry.stderrLines : entry.stdoutLines;
            appendBounded(target, line);

            String normalized = line.toLowerCase();
            if (isSuccessfulWatchLine(normalized)) {
                entry.lastBuildCompletedAt = nowUtc();
                entry.lastBuildSucceeded = true;
                entry.status = WorkspaceStatus.READY;
                entry.updatedAt = entry.lastBuildCompletedAt;
                entry.lastHeartbeatAt = entry.updatedAt;
                entry.monitor.notifyAll();
            } else if (isFailedWatchLine(normalized)) {
                entry.lastBuildCompletedAt = nowUtc();
                entry.lastBuildSucceeded = false;
                entry.status = WorkspaceStatus.FAILED;
                entry.updatedAt = entry.lastBuildCompletedAt;
                entry.lastHeartbeatAt = entry.updatedAt;
                entry.monitor.notifyAll();
            }
        }
    }

    public Optional<Process> resolveProcess(String workspaceId) {
        WorkspaceEntry entry = byWorkspaceId.get(workspaceId);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.process);
    }

    public List<String> findExpiredWorkspaceIds(OffsetDateTime cutoff) {
        return byWorkspaceId.values().stream()
                .filter(entry -> entry.lastHeartbeatAt != null && entry.lastHeartbeatAt.isBefore(cutoff))
                .map(entry -> entry.workspaceId)
                .toList();
    }

    public Optional<String> registerPreview(String sessionId, String previewId) {
        if (sessionId == null || sessionId.isBlank() || previewId == null || previewId.isBlank()) {
            return Optional.empty();
        }
        String workspaceId = workspaceIdBySessionId.get(sessionId);
        if (workspaceId == null) {
            return Optional.empty();
        }
        WorkspaceEntry entry = byWorkspaceId.get(workspaceId);
        if (entry == null) {
            return Optional.empty();
        }
        entry.latestPreviewId = previewId;
        entry.updatedAt = nowUtc();
        entry.lastHeartbeatAt = entry.updatedAt;
        return Optional.of(workspaceId);
    }

    public Optional<String> resolvePreviewId(String workspaceId) {
        WorkspaceEntry entry = byWorkspaceId.get(workspaceId);
        if (entry == null || entry.latestPreviewId == null || entry.latestPreviewId.isBlank()) {
            return Optional.empty();
        }
        entry.updatedAt = nowUtc();
        entry.lastHeartbeatAt = entry.updatedAt;
        return Optional.of(entry.latestPreviewId);
    }

    private WorkspaceResponse toResponse(WorkspaceEntry entry) {
        return WorkspaceResponse.builder()
                .sessionId(entry.sessionId)
                .workspaceId(entry.workspaceId)
                .language(entry.language)
                .status(entry.status)
                .previewPath(entry.previewPath)
                .createdAt(entry.createdAt)
                .updatedAt(entry.updatedAt)
                .lastHeartbeatAt(entry.lastHeartbeatAt)
                .build();
    }

    private WorkspaceBuildState toBuildState(WorkspaceEntry entry) {
        return WorkspaceBuildState.builder()
                .workspaceId(entry.workspaceId)
                .status(entry.status)
                .succeeded(Boolean.TRUE.equals(entry.lastBuildSucceeded))
                .stdout(String.join("\n", entry.stdoutLines))
                .stderr(String.join("\n", entry.stderrLines))
                .lastBuildRequestedAt(entry.lastBuildRequestedAt)
                .lastBuildCompletedAt(entry.lastBuildCompletedAt)
                .build();
    }

    private void appendBounded(ArrayDeque<String> lines, String line) {
        if (lines.size() >= 250) {
            lines.removeFirst();
        }
        lines.addLast(line);
    }

    private boolean isSuccessfulWatchLine(String normalizedLine) {
        return normalizedLine.contains("application bundle generation complete")
                || normalizedLine.contains("built in ")
                || normalizedLine.contains("build complete")
                || normalizedLine.contains("compiled successfully");
    }

    private boolean isFailedWatchLine(String normalizedLine) {
        return normalizedLine.contains("application bundle generation failed")
                || normalizedLine.contains("build failed")
                || normalizedLine.contains("failed in ")
                || normalizedLine.contains("transform failed")
                || normalizedLine.contains("error during build")
                || normalizedLine.contains(": error ")
                || normalizedLine.contains(" error ts")
                || normalizedLine.contains("does not provide an export named")
                || normalizedLine.contains("has no default export")
                || normalizedLine.contains("cannot find module")
                || normalizedLine.contains("unexpected token")
                || normalizedLine.contains("failed to resolve import")
                || normalizedLine.contains("could not resolve")
                || normalizedLine.contains("[plugin:vite:")
                || normalizedLine.startsWith("error");
    }

    private void destroyProcess(Process process) {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static final class WorkspaceEntry {
        private String sessionId;
        private String workspaceId;
        private ExecutionLanguage language;
        private WorkspaceStatus status;
        private String previewPath;
        private String latestPreviewId;
        private Path workspaceRoot;
        private Path previewRoot;
        private Process process;
        private final Object monitor = new Object();
        private final ArrayDeque<String> stdoutLines = new ArrayDeque<>();
        private final ArrayDeque<String> stderrLines = new ArrayDeque<>();
        private Boolean lastBuildSucceeded;
        private OffsetDateTime lastBuildRequestedAt;
        private OffsetDateTime lastBuildCompletedAt;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private OffsetDateTime lastHeartbeatAt;
    }

    @lombok.Builder
    @lombok.Value
    public static class WorkspaceBuildState {
        String workspaceId;
        WorkspaceStatus status;
        boolean succeeded;
        String stdout;
        String stderr;
        OffsetDateTime lastBuildRequestedAt;
        OffsetDateTime lastBuildCompletedAt;
    }
}
