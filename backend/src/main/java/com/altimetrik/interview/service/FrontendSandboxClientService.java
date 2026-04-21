package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.FrontendBuildRequest;
import com.altimetrik.interview.dto.FrontendBuildResponse;
import com.altimetrik.interview.dto.FrontendWorkspaceRequest;
import com.altimetrik.interview.dto.FrontendWorkspaceResponse;
import com.altimetrik.interview.dto.PatchFrontendWorkspaceFilesRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Objects;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
@Slf4j
public class FrontendSandboxClientService {

    @Qualifier("sandboxFrontendRestClient")
    private final RestClient sandboxFrontendRestClient;
    @Value("${sandbox.frontend-public-base-url:http://localhost:8083/api}")
    private String sandboxFrontendPublicBaseUrl;

    public FrontendBuildResponse build(FrontendBuildRequest request) {
        try {
            return Objects.requireNonNull(sandboxFrontendRestClient.post()
                    .uri("/workspace/build")
                    .body(request)
                    .retrieve()
                    .body(FrontendBuildResponse.class), "Frontend sandbox response was empty for /workspace/build");
        } catch (RestClientException ex) {
            log.error("Frontend sandbox request failed for /workspace/build", ex);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Frontend sandbox service is unavailable");
        }
    }

    public FrontendWorkspaceResponse createWorkspace(FrontendWorkspaceRequest request) {
        return normalizeWorkspacePreview(post("/workspaces", request, FrontendWorkspaceResponse.class));
    }

    public FrontendWorkspaceResponse getWorkspaceBySessionId(String sessionId) {
        try {
            return normalizeWorkspacePreview(Objects.requireNonNull(sandboxFrontendRestClient.get()
                    .uri("/workspaces/session/{sessionId}", sessionId)
                    .retrieve()
                    .body(FrontendWorkspaceResponse.class), "Frontend sandbox response was empty for /workspaces/session/" + sessionId));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 404) {
                return null;
            }
            log.error("Frontend sandbox request failed for /workspaces/session/{}", sessionId, ex);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Frontend sandbox service is unavailable");
        } catch (RestClientException ex) {
            log.error("Frontend sandbox request failed for /workspaces/session/{}", sessionId, ex);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Frontend sandbox service is unavailable");
        }
    }

    public void deleteWorkspace(String workspaceId) {
        try {
            sandboxFrontendRestClient.delete()
                    .uri("/workspaces/{workspaceId}", workspaceId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ex) {
            return;
        } catch (RestClientException ex) {
            log.error("Frontend sandbox request failed for /workspaces/{}", workspaceId, ex);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Frontend sandbox service is unavailable");
        }
    }

    public FrontendWorkspaceResponse patchWorkspaceFiles(String workspaceId, PatchFrontendWorkspaceFilesRequest request) {
        try {
            return normalizeWorkspacePreview(Objects.requireNonNull(sandboxFrontendRestClient.patch()
                    .uri("/workspaces/{workspaceId}/files", workspaceId)
                    .body(request)
                    .retrieve()
                    .body(FrontendWorkspaceResponse.class), "Frontend sandbox response was empty for /workspaces/" + workspaceId + "/files"));
        } catch (RestClientException ex) {
            log.error("Frontend sandbox request failed for /workspaces/{}/files", workspaceId, ex);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Frontend sandbox service is unavailable");
        }
    }

    public byte[] downloadPreviewArchive(String previewUrl) {
        String workspaceId = extractWorkspaceId(previewUrl);
        if (workspaceId == null) {
            log.warn("Skipping preview archive download because workspaceId could not be resolved from previewUrl={}", previewUrl);
            return null;
        }

        try {
            byte[] archive = sandboxFrontendRestClient.get()
                    .uri("/workspaces/{workspaceId}/preview/archive", workspaceId)
                    .retrieve()
                    .body(byte[].class);
            log.info("Downloaded frontend preview archive workspaceId={} previewUrl={} archiveBytes={}",
                    workspaceId,
                    previewUrl,
                    archive == null ? 0 : archive.length);
            return archive;
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Frontend preview archive not found for workspaceId={} previewUrl={}", workspaceId, previewUrl);
            return null;
        } catch (RestClientException ex) {
            log.error("Frontend sandbox request failed for /workspaces/{}/preview/archive", workspaceId, ex);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Frontend sandbox service is unavailable");
        }
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        try {
            return Objects.requireNonNull(sandboxFrontendRestClient.post()
                    .uri(path)
                    .body(request)
                    .retrieve()
                    .body(responseType), "Frontend sandbox response was empty for " + path);
        } catch (RestClientException ex) {
            log.error("Frontend sandbox request failed for {}", path, ex);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Frontend sandbox service is unavailable");
        }
    }

    private FrontendWorkspaceResponse normalizeWorkspacePreview(FrontendWorkspaceResponse response) {
        if (response == null) {
            return null;
        }
        response.setPreviewPath(resolveFrontendPreviewUrl(response.getPreviewPath()));
        return response;
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

    private String extractWorkspaceId(String previewUrl) {
        if (previewUrl == null || previewUrl.isBlank()) {
            return null;
        }

        String path = previewUrl;
        try {
            path = URI.create(previewUrl).getPath();
        } catch (IllegalArgumentException ignored) {
            // Fall back to raw string parsing if the preview URL is already a relative path.
        }

        if (path == null || path.isBlank()) {
            return null;
        }

        String marker = "/workspaces/";
        int workspaceIndex = path.indexOf(marker);
        if (workspaceIndex < 0) {
            return null;
        }
        String suffix = path.substring(workspaceIndex + marker.length());
        int slashIndex = suffix.indexOf('/');
        if (slashIndex < 0) {
            return suffix.isBlank() ? null : suffix;
        }
        String workspaceId = suffix.substring(0, slashIndex);
        return workspaceId.isBlank() ? null : workspaceId;
    }
}
