package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.SessionStatus;
import com.altimetrik.interview.enums.TechnologySkill;
import com.altimetrik.interview.enums.ParticipantRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class SessionResponse {
    private String id;
    private TechnologySkill technology;
    private SessionStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Integer durationSec;
    private Integer remainingSec;
    private Boolean extensionUsed;
    private Boolean readOnly;
    private List<ParticipantDto> participants;
    private String latestCode;
    private List<EditableCodeFileDto> codeFiles;
    private Long codeVersion;
    private RunResultDto finalRunResult;
    private FeedbackDto feedback;
    private FeedbackDto feedbackDraft;
    private List<ActivityEventDto> activityEvents;
    private JoinTokenResponse joinInfo;
    private String summary;
    private OffsetDateTime interruptedAt;
    private OffsetDateTime recoveryDeadlineAt;
    private ParticipantRole recoveryRequiredRole;
    private Boolean suspiciousRejected;
    private String suspiciousScenarioKey;
    private String suspiciousActivityReason;
    private FrontendWorkspaceDto frontendWorkspace;
    private String finalPreviewUrl;
}
