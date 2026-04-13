package com.altimetrik.interview.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "session_tokens")
public class SessionToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String token;

    private String sessionId;
    
    @Enumerated(EnumType.STRING)
    private com.altimetrik.interview.enums.ParticipantRole role;
    
    private String expectedEmail;
    
    private OffsetDateTime expiresAt;
    
    private OffsetDateTime usedAt;
    
    private Boolean isUsed = false;
    
    @CreationTimestamp
    private OffsetDateTime createdAt;
    
    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
