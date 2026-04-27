package com.altimetrik.interview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
        name = "code_files",
        indexes = {
                @Index(name = "idx_code_files_session_sort", columnList = "sessionId, sortOrder"),
                @Index(name = "idx_code_files_session_path", columnList = "sessionId, filePath", unique = true)
        }
)
public class CodeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String sessionId;

    private String filePath;

    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Integer sortOrder;

    private Boolean editable;

    private Boolean enabledForCandidate;

    private Boolean activeQuestion;

    private Boolean submitted;

    private Integer idealDurationMinutes;

    private OffsetDateTime candidateStartedAt;

    private OffsetDateTime submittedAt;

    private Long solveDurationSeconds;

    private Integer executeAttemptCount;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
