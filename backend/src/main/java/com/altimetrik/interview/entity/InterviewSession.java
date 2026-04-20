package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.SessionStatus;
import com.altimetrik.interview.enums.TechnologySkill;
import com.altimetrik.interview.enums.FeedbackRating;
import com.altimetrik.interview.enums.ParticipantRole;
import com.altimetrik.interview.enums.RecommendationDecision;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "interview_sessions")
public class InterviewSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @CreationTimestamp
    private OffsetDateTime createdAt;
    
    private OffsetDateTime startedAt;
    
    private OffsetDateTime endedAt;

    private OffsetDateTime interruptedAt;

    private OffsetDateTime recoveryDeadlineAt;

    @Enumerated(EnumType.STRING)
    private ParticipantRole recoveryRequiredRole;

    private Boolean suspiciousRejected = false;

    private String suspiciousScenarioKey;

    @Column(columnDefinition = "TEXT")
    private String suspiciousActivityReason;

    @Column(columnDefinition = "TEXT")
    private String suspiciousActivityHistory;

    @Enumerated(EnumType.STRING)
    private FeedbackRating feedbackDraftRating;

    @Column(columnDefinition = "TEXT")
    private String feedbackDraftComments;

    @Enumerated(EnumType.STRING)
    private RecommendationDecision feedbackDraftRecommendationDecision;
    
    private Integer durationSec;

    private Boolean extensionUsed = false;

    private Boolean incomplete = false;

    @Enumerated(EnumType.STRING)
    private TechnologySkill technology = TechnologySkill.JAVA;
    
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.CREATED;
}
