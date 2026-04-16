package com.altimetrik.interview.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "participants")
public class Participant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String sessionId;
    
    @Enumerated(EnumType.STRING)
    private com.altimetrik.interview.enums.ParticipantRole role;
    
    private String name;
    
    private String email;

    private String timeZone;
    
    private OffsetDateTime disclaimerAcceptedAt;
    
    @CreationTimestamp
    private OffsetDateTime joinedAt;
}
