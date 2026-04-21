package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.FrontendWorkspaceStatus;
import com.altimetrik.interview.enums.TechnologySkill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "frontend_workspaces")
public class FrontendWorkspace {

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    private String sessionId;

    @Column(nullable = false, unique = true)
    private String workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TechnologySkill technology;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FrontendWorkspaceStatus status;

    @Column(columnDefinition = "TEXT")
    private String previewUrl;

    private String sandboxInstance;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime lastHeartbeatAt;
}
