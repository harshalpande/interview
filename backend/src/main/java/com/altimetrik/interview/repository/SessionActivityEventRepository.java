package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.SessionActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionActivityEventRepository extends JpaRepository<SessionActivityEvent, String> {
    List<SessionActivityEvent> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
