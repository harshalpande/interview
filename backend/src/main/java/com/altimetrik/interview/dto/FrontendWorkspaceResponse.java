package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ExecutionLanguage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrontendWorkspaceResponse {
    private String sessionId;
    private String workspaceId;
    private ExecutionLanguage language;
    private String status;
    private String previewPath;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastHeartbeatAt;
}
