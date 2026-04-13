package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.CodeState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CodeStateRepository extends JpaRepository<CodeState, String> {
    Optional<CodeState> findBySessionId(String sessionId);
}
