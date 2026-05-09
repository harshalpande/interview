package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.QuestionSourceFlow;
import com.altimetrik.interview.enums.TechnologySkill;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(
        name = "candidate_question_history",
        indexes = {
                @Index(name = "idx_candidate_question_combo", columnList = "emailNormalized,technology,experienceBand,targetRoleKey,evaluationStyle"),
                @Index(name = "idx_candidate_question_question", columnList = "questionId"),
                @Index(name = "idx_candidate_question_series", columnList = "seriesId")
        }
)
public class CandidateQuestionHistory {

    @Id
    private String id;

    private String emailNormalized;

    @Enumerated(EnumType.STRING)
    private TechnologySkill technology;

    private String experienceBand;

    private String targetRole;

    private String targetRoleKey;

    @Enumerated(EnumType.STRING)
    private EvaluationStyle evaluationStyle;

    private String questionId;

    private String seriesId;

    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    private QuestionSourceFlow sourceFlow;

    private String sourceId;

    @CreationTimestamp
    private OffsetDateTime assignedAt;
}
