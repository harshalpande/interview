package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.CandidateQuestionHistory;
import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.TechnologySkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CandidateQuestionHistoryRepository extends JpaRepository<CandidateQuestionHistory, String> {
    List<CandidateQuestionHistory> findByEmailNormalizedAndTechnologyAndExperienceBandAndTargetRoleKeyAndEvaluationStyle(
            String emailNormalized,
            TechnologySkill technology,
            String experienceBand,
            String targetRoleKey,
            EvaluationStyle evaluationStyle
    );

    boolean existsByEmailNormalizedAndTechnologyAndExperienceBandAndTargetRoleKeyAndEvaluationStyleAndQuestionId(
            String emailNormalized,
            TechnologySkill technology,
            String experienceBand,
            String targetRoleKey,
            EvaluationStyle evaluationStyle,
            String questionId
    );

    @Query("""
            select h.questionId, count(h)
            from CandidateQuestionHistory h
            where h.technology = :technology
              and h.experienceBand = :experienceBand
              and h.targetRoleKey = :targetRoleKey
              and h.evaluationStyle = :evaluationStyle
              and h.questionId is not null
            group by h.questionId
            """)
    List<Object[]> countQuestionUsage(
            TechnologySkill technology,
            String experienceBand,
            String targetRoleKey,
            EvaluationStyle evaluationStyle
    );

    @Query("""
            select h.seriesId, count(h)
            from CandidateQuestionHistory h
            where h.technology = :technology
              and h.experienceBand = :experienceBand
              and h.targetRoleKey = :targetRoleKey
              and h.evaluationStyle = :evaluationStyle
              and h.seriesId is not null
            group by h.seriesId
            """)
    List<Object[]> countSeriesUsage(
            TechnologySkill technology,
            String experienceBand,
            String targetRoleKey,
            EvaluationStyle evaluationStyle
    );
}
