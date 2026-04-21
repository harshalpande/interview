package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.FrontendWorkspaceStatus;
import com.altimetrik.interview.enums.TechnologySkill;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class FrontendWorkspaceDto {
    private String sessionId;
    private String workspaceId;
    private TechnologySkill technology;
    private FrontendWorkspaceStatus status;
    private String previewUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastHeartbeatAt;
}
