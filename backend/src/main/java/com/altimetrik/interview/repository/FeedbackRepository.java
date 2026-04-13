package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    Optional<Feedback> findBySessionId(String sessionId);
}

