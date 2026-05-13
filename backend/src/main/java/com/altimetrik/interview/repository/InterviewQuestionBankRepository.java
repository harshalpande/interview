package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.InterviewQuestionBank;
import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.QuestionStarterType;
import com.altimetrik.interview.enums.TechnologySkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewQuestionBankRepository extends JpaRepository<InterviewQuestionBank, String> {
    List<InterviewQuestionBank> findByTechnologyAndActiveTrueOrderByDifficultyLevelAscTitleAsc(TechnologySkill technology);

    List<InterviewQuestionBank> findBySeriesIdAndActiveTrueOrderBySequenceNumberAsc(String seriesId);

    Optional<InterviewQuestionBank> findFirstBySeriesIdAndSequenceNumberAndActiveTrue(String seriesId, Integer sequenceNumber);

    List<InterviewQuestionBank> findByTechnologyAndEvaluationStyleAndExperienceBandAndActiveTrueOrderByDifficultyLevelAscTitleAsc(
            TechnologySkill technology,
            EvaluationStyle evaluationStyle,
            String experienceBand
    );

    Optional<InterviewQuestionBank> findFirstBySeriesIdAndSequenceNumberAndStarterTypeAndActiveTrue(
            String seriesId,
            Integer sequenceNumber,
            QuestionStarterType starterType
    );
}
