package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.TechnologySkill;
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
                @Index(name = "idx_question_bank_active", columnList = "active")
        }
)
public class InterviewQuestionBank {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private TechnologySkill technology;

    private Integer difficultyLevel;

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
