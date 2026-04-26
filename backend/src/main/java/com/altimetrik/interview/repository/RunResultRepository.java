package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.RunResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RunResultRepository extends JpaRepository<RunResult, String> {
    Optional<RunResult> findTopBySessionIdOrderByCompiledAtDesc(String sessionId);

    Optional<RunResult> findTopBySessionIdAndFilePathIsNullOrderByCompiledAtDesc(String sessionId);

    List<RunResult> findBySessionIdAndFilePathIsNotNullOrderByCompiledAtDesc(String sessionId);

    Optional<RunResult> findTopBySessionIdAndFilePathOrderByCompiledAtDesc(String sessionId, String filePath);
}
