package com.altimetrik.interview.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "code_states")
public class CodeState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String sessionId;
    
    @Column(columnDefinition = "TEXT")
    private String latestCode;
    
    private OffsetDateTime updatedAt;
    
    private String updatedByRole;
    
    private Long version;
    
    @CreationTimestamp
    private OffsetDateTime createdAt;
    
    @UpdateTimestamp
    private OffsetDateTime lastModifiedAt;
}
