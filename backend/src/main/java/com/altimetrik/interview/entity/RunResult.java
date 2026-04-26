package com.altimetrik.interview.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "run_results")
public class RunResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String sessionId;

    private String filePath;

    private String displayName;
    
    @CreationTimestamp
    private OffsetDateTime compiledAt;

    @Column(columnDefinition = "TEXT")
    private String sourceSnapshot;
    
    @Column(columnDefinition = "TEXT")
    private String stdout;
    
    @Column(columnDefinition = "TEXT")
    private String stderr;
    
    private Integer exitStatus;

    private Long executionTimeMs;
}
