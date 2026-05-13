package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.PreparationQuestionStatus;
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
        name = "preparation_question_assignment",
        indexes = {
                @Index(name = "idx_prep_assignment_attempt", columnList = "attemptId,sequenceNumber"),
                @Index(name = "idx_prep_assignment_email_question", columnList = "emailNormalized,questionId"),
                @Index(name = "idx_prep_assignment_email_series", columnList = "emailNormalized,seriesId")
        }
)
public class PreparationQuestionAssignment {

    @Id
    private String id;

    private String attemptId;

    private String emailNormalized;

    private String questionId;

    private String seriesId;

    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    private PreparationQuestionStatus status = PreparationQuestionStatus.ASSIGNED;

    private OffsetDateTime assignedAt;

    private OffsetDateTime submittedAt;

    private OffsetDateTime passedAt;

    private Integer executeAttemptCount = 0;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
