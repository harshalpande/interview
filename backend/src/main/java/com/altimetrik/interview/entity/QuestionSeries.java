package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.QuestionSource;
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
        name = "question_series",
        indexes = {
                @Index(name = "idx_question_series_lookup", columnList = "technology,evaluationStyle,experienceBand,active"),
                @Index(name = "idx_question_series_family", columnList = "problemFamilyKey")
        }
)
public class QuestionSeries {

    @Id
    private String id;

    private String title;

    @Enumerated(EnumType.STRING)
    private TechnologySkill technology;

    @Enumerated(EnumType.STRING)
    private EvaluationStyle evaluationStyle = EvaluationStyle.BANYAN;

    private String experienceBand;

    private String targetRole;

    private String problemFamilyKey;

    @Column(columnDefinition = "TEXT")
    private String problemFamilyDescription;

    @Enumerated(EnumType.STRING)
    private QuestionSource source = QuestionSource.SEEDED;

    private Boolean active = true;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
