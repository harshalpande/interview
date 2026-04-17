package com.altimetrik.interview.entity;

import com.altimetrik.interview.enums.ActivityEventType;
import com.altimetrik.interview.enums.ParticipantRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "session_activity_events")
public class SessionActivityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ParticipantRole participantRole;

    @Enumerated(EnumType.STRING)
    @Column(length = 64, columnDefinition = "VARCHAR(64)")
    private ActivityEventType eventType;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @CreationTimestamp
    private OffsetDateTime createdAt;
}
