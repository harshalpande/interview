package com.altimetrik.interview.service;

import com.altimetrik.interview.entity.CandidateQuestionHistory;
import com.altimetrik.interview.entity.InterviewQuestionBank;
import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.QuestionSourceFlow;
import com.altimetrik.interview.enums.TechnologySkill;
import com.altimetrik.interview.repository.CandidateQuestionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidateQuestionHistoryService {

    private final CandidateQuestionHistoryRepository repository;

    public List<CandidateQuestionHistory> history(String email,
                                                  TechnologySkill technology,
                                                  Integer yearsOfExperience,
                                                  String targetRole,
                                                  EvaluationStyle evaluationStyle) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || technology == null) {
            return List.of();
        }
        return repository.findByEmailNormalizedAndTechnologyAndExperienceBandAndTargetRoleKeyAndEvaluationStyle(
                normalizedEmail,
                technology,
                experienceBand(yearsOfExperience),
                targetRoleKey(targetRole),
                evaluationStyle == null ? EvaluationStyle.STANDARD_MULTIPLE_QUESTIONS : evaluationStyle
        );
    }

    public Set<String> seenQuestionIds(String email,
                                       TechnologySkill technology,
                                       Integer yearsOfExperience,
                                       String targetRole,
                                       EvaluationStyle evaluationStyle) {
        return history(email, technology, yearsOfExperience, targetRole, evaluationStyle).stream()
                .map(CandidateQuestionHistory::getQuestionId)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
    }

    public Set<String> seenSeriesIds(String email,
                                     TechnologySkill technology,
                                     Integer yearsOfExperience,
                                     String targetRole,
                                     EvaluationStyle evaluationStyle) {
        return history(email, technology, yearsOfExperience, targetRole, evaluationStyle).stream()
                .map(CandidateQuestionHistory::getSeriesId)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
    }

    public Map<String, Long> questionUsageCounts(TechnologySkill technology,
                                                 Integer yearsOfExperience,
                                                 String targetRole,
                                                 EvaluationStyle evaluationStyle) {
        if (technology == null) {
            return Map.of();
        }
        return toUsageMap(repository.countQuestionUsage(
                technology,
                experienceBand(yearsOfExperience),
                targetRoleKey(targetRole),
                evaluationStyle == null ? EvaluationStyle.STANDARD_MULTIPLE_QUESTIONS : evaluationStyle
        ));
    }

    public Map<String, Long> seriesUsageCounts(TechnologySkill technology,
                                               Integer yearsOfExperience,
                                               String targetRole,
                                               EvaluationStyle evaluationStyle) {
        if (technology == null) {
            return Map.of();
        }
        return toUsageMap(repository.countSeriesUsage(
                technology,
                experienceBand(yearsOfExperience),
                targetRoleKey(targetRole),
                evaluationStyle == null ? EvaluationStyle.STANDARD_MULTIPLE_QUESTIONS : evaluationStyle
        ));
    }

    public void recordAssignment(String email,
                                 TechnologySkill technology,
                                 Integer yearsOfExperience,
                                 String targetRole,
                                 EvaluationStyle evaluationStyle,
                                 InterviewQuestionBank question,
                                 QuestionSourceFlow sourceFlow,
                                 String sourceId) {
        if (question == null || question.getId() == null || question.getId().isBlank()) {
            return;
        }
        recordAssignment(
                email,
                technology,
                yearsOfExperience,
                targetRole,
                evaluationStyle,
                question.getId(),
                question.getSeriesId(),
                question.getSequenceNumber(),
                sourceFlow,
                sourceId
        );
    }

    public void recordAssignment(String email,
                                 TechnologySkill technology,
                                 Integer yearsOfExperience,
                                 String targetRole,
                                 EvaluationStyle evaluationStyle,
                                 String questionId,
                                 String seriesId,
                                 Integer sequenceNumber,
                                 QuestionSourceFlow sourceFlow,
                                 String sourceId) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || technology == null || questionId == null || questionId.isBlank()) {
            return;
        }
        EvaluationStyle style = evaluationStyle == null ? EvaluationStyle.STANDARD_MULTIPLE_QUESTIONS : evaluationStyle;
        String band = experienceBand(yearsOfExperience);
        String roleKey = targetRoleKey(targetRole);
        if (repository.existsByEmailNormalizedAndTechnologyAndExperienceBandAndTargetRoleKeyAndEvaluationStyleAndQuestionId(
                normalizedEmail, technology, band, roleKey, style, questionId)) {
            return;
        }
        CandidateQuestionHistory history = new CandidateQuestionHistory();
        history.setId(UUID.randomUUID().toString());
        history.setEmailNormalized(normalizedEmail);
        history.setTechnology(technology);
        history.setExperienceBand(band);
        history.setTargetRole(targetRole == null ? null : targetRole.trim());
        history.setTargetRoleKey(roleKey);
        history.setEvaluationStyle(style);
        history.setQuestionId(questionId);
        history.setSeriesId(seriesId);
        history.setSequenceNumber(sequenceNumber);
        history.setSourceFlow(sourceFlow);
        history.setSourceId(sourceId);
        repository.save(history);
    }

    public String experienceBand(Integer years) {
        int value = years == null ? 0 : Math.max(0, years);
        if (value <= 3) {
            return "1-3";
        }
        if (value <= 6) {
            return "4-6";
        }
        if (value <= 9) {
            return "7-9";
        }
        return "10+";
    }

    public String targetRoleKey(String targetRole) {
        if (targetRole == null || targetRole.isBlank()) {
            return "unspecified";
        }
        String normalized = targetRole.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "unspecified" : normalized;
    }

    public String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Long> toUsageMap(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> row != null && row.length >= 2 && row[0] != null && row[1] instanceof Number)
                .collect(Collectors.toMap(
                        row -> String.valueOf(row[0]),
                        row -> ((Number) row[1]).longValue()
                ));
    }
}
