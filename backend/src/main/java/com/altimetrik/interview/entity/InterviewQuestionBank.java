package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.TechnologySkill;
import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.QuestionSource;
import com.altimetrik.interview.enums.QuestionStarterType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(
        name = "interview_question_bank",
        indexes = {
                @Index(name = "idx_question_bank_technology_level", columnList = "technology, difficultyLevel"),
                @Index(name = "idx_question_bank_active", columnList = "active"),
                @Index(name = "idx_question_bank_series_sequence", columnList = "seriesId, sequenceNumber"),
                @Index(name = "idx_question_bank_prep_lookup", columnList = "technology, evaluationStyle, experienceBand, active")
        }
)
public class InterviewQuestionBank {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private TechnologySkill technology;

    private Integer difficultyLevel;

    private String seriesId;

    private Integer sequenceNumber;

    private Integer banyanLevel;

    @Enumerated(EnumType.STRING)
    private EvaluationStyle evaluationStyle = EvaluationStyle.STANDARD_MULTIPLE_QUESTIONS;

    private String experienceBand;

    private String targetRole;

    private String problemFamilyKey;

    @Enumerated(EnumType.STRING)
    private QuestionStarterType starterType;

    @Enumerated(EnumType.STRING)
    private QuestionSource source = QuestionSource.SEEDED;

    private String title;

    private String filePath;

    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String problemStatement;

    @Column(columnDefinition = "TEXT")
    private String starterCode;

    @Column(columnDefinition = "TEXT")
    private String referenceSolution;

    private Integer idealDurationMinutes;

    private String expectedTimeComplexity;

    private String expectedSpaceComplexity;

    @Column(columnDefinition = "TEXT")
    private String concepts;

    @Column(columnDefinition = "TEXT")
    private String evaluationFocus;

    private Boolean active = true;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
