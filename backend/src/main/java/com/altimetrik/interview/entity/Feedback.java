package com.altimetrik.interview.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "feedbacks")
public class Feedback {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String sessionId;
    
@Enumerated(EnumType.STRING)
    private com.altimetrik.interview.enums.FeedbackRating rating;
    
    @Column(columnDefinition = "TEXT")
    private String comments;
    
    private Boolean recommendation;
    
    @CreationTimestamp
    private OffsetDateTime submittedAt;
}
