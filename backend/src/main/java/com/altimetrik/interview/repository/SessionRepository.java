package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.InterviewSession;
import com.altimetrik.interview.enums.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<InterviewSession, String> {
    Page<InterviewSession> findByStatus(SessionStatus status, Pageable pageable);
    java.util.List<InterviewSession> findByStatus(SessionStatus status);
}
