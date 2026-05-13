package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.PreparationQuestionAssignment;
import com.altimetrik.interview.enums.PreparationQuestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PreparationQuestionAssignmentRepository extends JpaRepository<PreparationQuestionAssignment, String> {
    List<PreparationQuestionAssignment> findByAttemptIdOrderBySequenceNumberAsc(String attemptId);

    List<PreparationQuestionAssignment> findByEmailNormalized(String emailNormalized);

    Optional<PreparationQuestionAssignment> findByAttemptIdAndQuestionId(String attemptId, String questionId);

    Optional<PreparationQuestionAssignment> findByAttemptIdAndStatus(String attemptId, PreparationQuestionStatus status);
}
