package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class SessionResponse {
    private String id;
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
    private Long codeVersion;
    private RunResultDto finalRunResult;
    private FeedbackDto feedback;
    private JoinTokenResponse joinInfo;
    private String summary;
}
