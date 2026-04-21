package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.CodeFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CodeFileRepository extends JpaRepository<CodeFile, String> {
    List<CodeFile> findBySessionIdOrderBySortOrderAscCreatedAtAsc(String sessionId);

    Optional<CodeFile> findBySessionIdAndFilePath(String sessionId, String filePath);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CodeFile c where c.sessionId = :sessionId")
    void deleteAllBySessionId(@Param("sessionId") String sessionId);
}
