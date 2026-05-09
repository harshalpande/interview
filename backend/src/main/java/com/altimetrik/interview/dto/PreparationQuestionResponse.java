package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.QuestionStarterType;
import com.altimetrik.interview.enums.TechnologySkill;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class PreparationQuestionResponse {
    private String attemptId;
    private String questionId;
    private String seriesId;
    private TechnologySkill technology;
    private String title;
    private String displayName;
    private String filePath;
    private String problemStatement;
    private String starterCode;
    private Integer sequenceNumber;
    private Integer banyanLevel;
    private QuestionStarterType starterType;
    private String experienceBand;
    private OffsetDateTime questionStartedAt;
    private OffsetDateTime questionExpiresAt;
    private long remainingSeconds;
    private OffsetDateTime attemptExpiresAt;
    private long remainingAttemptSeconds;
    private Integer executeAttemptCount;
    private boolean attemptEnded;
    private String message;
    private List<String> concepts;
}
