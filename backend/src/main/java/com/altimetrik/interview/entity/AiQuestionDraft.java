package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.TechnologySkill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(
        name = "ai_question_drafts",
        indexes = {
                @Index(name = "idx_ai_question_drafts_session", columnList = "sessionId, createdAt")
        }
)
public class AiQuestionDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String sessionId;

    @Enumerated(EnumType.STRING)
    private TechnologySkill technology;

    private Integer questionNumber;

    private String title;

    private String filePath;

    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String problemStatement;

    @Column(columnDefinition = "TEXT")
    private String starterCode;

    @Column(columnDefinition = "TEXT")
    private String referenceSolution;

    private Integer difficultyLevel;

    private Integer idealDurationMinutes;

    private String expectedTimeComplexity;

    private String expectedSpaceComplexity;

    @Column(columnDefinition = "TEXT")
    private String concepts;

    @Column(columnDefinition = "TEXT")
    private String evaluationFocus;

    private String section;

    private Boolean accepted = false;

    private OffsetDateTime acceptedAt;

    @CreationTimestamp
    private OffsetDateTime createdAt;
}
