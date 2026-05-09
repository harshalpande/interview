package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.PreparationAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PreparationAttemptRepository extends JpaRepository<PreparationAttempt, String> {
    Optional<PreparationAttempt> findBySecureToken(String secureToken);

    List<PreparationAttempt> findByEmailNormalizedOrderByCreatedAtDesc(String emailNormalized);

    long countByEmailNormalizedAndCreatedAtGreaterThanEqual(String emailNormalized, OffsetDateTime createdAt);

    Page<PreparationAttempt> findByEmailNormalizedContainingIgnoreCaseOrCandidateNameContainingIgnoreCase(
            String email,
            String candidateName,
            Pageable pageable
    );
}
