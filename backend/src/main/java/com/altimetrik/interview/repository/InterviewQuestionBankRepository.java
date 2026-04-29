package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.InterviewQuestionBank;
import com.altimetrik.interview.enums.TechnologySkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewQuestionBankRepository extends JpaRepository<InterviewQuestionBank, String> {
    List<InterviewQuestionBank> findByTechnologyAndActiveTrueOrderByDifficultyLevelAscTitleAsc(TechnologySkill technology);
}
