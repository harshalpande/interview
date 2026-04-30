package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.AiQuestionDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiQuestionDraftRepository extends JpaRepository<AiQuestionDraft, String> {
    Optional<AiQuestionDraft> findByIdAndSessionId(String id, String sessionId);
}
