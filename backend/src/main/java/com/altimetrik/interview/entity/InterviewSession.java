package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.SessionStatus;
import com.altimetrik.interview.enums.TechnologySkill;
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
    
    private Integer durationSec;

    private Boolean extensionUsed = false;

    private Boolean incomplete = false;

    @Enumerated(EnumType.STRING)
    private TechnologySkill technology = TechnologySkill.JAVA;
    
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.CREATED;
}
