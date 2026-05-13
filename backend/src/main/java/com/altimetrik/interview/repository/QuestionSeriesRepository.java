package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.QuestionSeries;
import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.TechnologySkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionSeriesRepository extends JpaRepository<QuestionSeries, String> {
    List<QuestionSeries> findByTechnologyAndEvaluationStyleAndExperienceBandAndActiveTrueOrderByCreatedAtAsc(
            TechnologySkill technology,
            EvaluationStyle evaluationStyle,
            String experienceBand
    );
}
