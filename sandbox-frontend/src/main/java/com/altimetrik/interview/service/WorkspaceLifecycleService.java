package com.altimetrik.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceLifecycleService {

    private final FrontendSandboxService frontendSandboxService;
    private final WorkspaceRegistryService workspaceRegistryService;

    @Value("${app.workspace.idle-timeout:PT30M}")
    private Duration idleTimeout;

    @Scheduled(fixedDelayString = "${app.workspace.cleanup-interval-ms:60000}")
    public void cleanupExpiredWorkspaces() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(idleTimeout);
        for (String workspaceId : workspaceRegistryService.findExpiredWorkspaceIds(cutoff)) {
            try {
                frontendSandboxService.deleteWorkspace(workspaceId);
                log.info("Cleaned up expired frontend workspace {}", workspaceId);
            } catch (Exception exception) {
                log.warn("Failed to clean up expired frontend workspace {}", workspaceId, exception);
            }
        }
    }
}
