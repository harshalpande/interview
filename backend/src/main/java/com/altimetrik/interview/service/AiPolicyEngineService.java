package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.EditableCodeFileDto;
import com.altimetrik.interview.entity.InterviewSession;
import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.TechnologySkill;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AiPolicyEngineService {

    public QuestionPolicyPlan questionPolicy(InterviewSession session,
                                             int difficultyLevel,
                                             int questionNumber,
                                             List<EditableCodeFileDto> previousQuestions,
                                             int timeRemainingSeconds) {
        TechnologySkill technology = session.getTechnology() == null ? TechnologySkill.JAVA : session.getTechnology();
        List<String> targetConcepts = targetConcepts(technology, difficultyLevel, questionNumber);
        List<String> previousConcepts = previousConcepts(previousQuestions);
        List<String> avoidConcepts = avoidConcepts(previousConcepts, targetConcepts);
        List<String> forbiddenCapabilities = forbiddenCapabilities(technology);
        List<String> requiredElements = requiredQuestionElements(technology);
        int idealDurationMinutes = idealDurationMinutes(difficultyLevel, timeRemainingSeconds, session.getYearsOfExperience());
        String sandboxRules = sandboxRules(technology);
        String rubric = evaluationRubric(session, difficultyLevel);
        String styleGuidance = session.getEvaluationStyle() == EvaluationStyle.BANYAN
                ? "Evaluation style: Banyan Style. Generate one evolving challenge level in the same single file. Level 1 seeds a growable problem. Level 2+ must extend the same domain/classes/methods and include all previous requirements and validation expectations plus new assertions. Do not generate a separate or unrelated question."
                : "Evaluation style: Standard Multiple Questions. Generate a standalone independent question.";

        String questionPolicy = """
                Policy source: backend Question Policy/Rubric Engine v1.
                Generate exactly one sandbox-ready question for %s, target role %s, experience %s year(s), difficulty level %d.
                %s
                Target concept coverage: %s.
                Avoid repeated concepts/problem shapes: %s.
                Required question elements: %s.
                Forbidden capabilities: %s.
                Expected candidate duration: %d minutes.
                Calibrate question scope to the candidate experience and target role: fair, practical, sandbox-ready, and concept-focused rather than trick-heavy.
                Prefer questions that test reasoning, edge cases, and implementation clarity over memorized syntax, while keeping the evaluation standard intact.
                The question must be executable in the current sandbox and must include validation checks in starterCode.
                Do not ask for implementation that requires unsupported dependencies, file IO, network IO, databases, system processes, or external services.
                """.formatted(
                technology.name(),
                valueOrDefault(session.getTargetRole(), "unspecified"),
                session.getYearsOfExperience() == null ? "unspecified" : session.getYearsOfExperience(),
                difficultyLevel,
                styleGuidance,
                String.join(", ", targetConcepts),
                avoidConcepts.isEmpty() ? "none captured" : String.join(", ", avoidConcepts),
                String.join(", ", requiredElements),
                String.join(", ", forbiddenCapabilities),
                idealDurationMinutes
        );

        return new QuestionPolicyPlan(
                questionPolicy,
                rubric,
                targetConcepts,
                previousConcepts,
                avoidConcepts,
                forbiddenCapabilities,
                requiredElements,
                sandboxRules,
                idealDurationMinutes
        );
    }

    public EvaluationPolicy evaluationPolicy(InterviewSession session, EditableCodeFileDto question) {
        int difficultyLevel = normalizeDifficulty(question == null ? null : question.getDifficultyLevel());
        TechnologySkill technology = session.getTechnology() == null ? TechnologySkill.JAVA : session.getTechnology();
        List<String> concepts = splitConcepts(question == null ? null : question.getQuestionConcepts());
        if (concepts.isEmpty()) {
            concepts = targetConcepts(technology, difficultyLevel, questionNumberHint(question));
        }
        List<String> nonNegotiables = List.of(
                "Correctness against visible and hidden validation cases",
                "No tampering with original problem statement or validation assertions",
                "No unsupported dependency, file IO, network IO, database, or external process usage",
                "Runnable code in the configured sandbox"
        );
        String questionPolicy = """
                Evaluate using backend policy v1 for %s difficulty level %d.
                Expected concepts: %s.
                Non-negotiable checks: %s.
                Treat execution attempts as a confidence signal only; this editor does not provide debugger support.
                %s
                """.formatted(
                technology.name(),
                difficultyLevel,
                String.join(", ", concepts),
                String.join(", ", nonNegotiables),
                session.getEvaluationStyle() == EvaluationStyle.BANYAN
                        ? "Banyan evaluation: score the submitted level as part of one evolving challenge. Do not treat it as an unrelated separate question; verify previous requirements and new assertions still pass."
                        : "Standard evaluation: score this question independently."
        );
        return new EvaluationPolicy(questionPolicy, evaluationRubric(session, difficultyLevel), concepts, nonNegotiables);
    }

    public RecommendationPolicy recommendationPolicy(InterviewSession session) {
        int startingDifficulty = normalizeDifficulty(session.getStartingDifficultyLevel());
        String recommendationPolicy = """
                Recommendation policy source: backend Question Policy/Rubric Engine v1.
                Human review is mandatory. AI recommendation must be advisory and explain confidence, risks, and follow-up areas.
                Evaluate fit for target role %s with %s year(s) of experience in %s.
                Consider correctness, edge cases, code quality, efficiency, time taken, execution attempts, question integrity, and concept coverage.
                Do not reject solely for higher execution attempts when output, reasoning, and final code are strong.
                """.formatted(
                valueOrDefault(session.getTargetRole(), "unspecified"),
                session.getYearsOfExperience() == null ? "unspecified" : session.getYearsOfExperience(),
                session.getTechnology() == null ? TechnologySkill.JAVA.name() : session.getTechnology().name()
        );
        return new RecommendationPolicy(recommendationPolicy, evaluationRubric(session, startingDifficulty));
    }

    private List<String> targetConcepts(TechnologySkill technology, int difficultyLevel, int questionNumber) {
        return switch (technology) {
            case PYTHON -> switch (difficultyLevel) {
                case 1 -> List.of("strings", "lists", "loops", "basic functions");
                case 2 -> List.of("dictionaries", "sets", "sorting", "input validation");
                case 3 -> List.of("iterators", "generators", "collections module", "edge-case handling");
                case 4 -> List.of("itertools", "functools", "data modeling", "algorithmic efficiency");
                default -> List.of("advanced data transformations", "performance tradeoffs", "clean API design");
            };
            case ANGULAR -> switch (difficultyLevel) {
                case 1 -> List.of("component template binding", "events", "basic state");
                case 2 -> List.of("forms", "validation", "list rendering", "derived state");
                case 3 -> List.of("services", "observables", "component communication");
                case 4 -> List.of("reactive forms", "change detection", "accessibility");
                default -> List.of("architecture", "performance", "testable component design");
            };
            case REACT -> switch (difficultyLevel) {
                case 1 -> List.of("props", "state", "events", "conditional rendering");
                case 2 -> List.of("lists", "forms", "derived state", "controlled inputs");
                case 3 -> List.of("custom hooks", "component composition", "state lifting");
                case 4 -> List.of("memoization", "effect discipline", "accessibility");
                default -> List.of("architecture", "performance", "complex state orchestration");
            };
            default -> switch (difficultyLevel) {
                case 1 -> List.of("strings", "arrays", "loops", "basic methods");
                case 2 -> List.of("collections", "maps", "sets", "sorting");
                case 3 -> List.of("object-oriented design", "interfaces", "streams", "lambda expressions");
                case 4 -> List.of("generics", "advanced collections", "thread-safety basics", "algorithmic efficiency");
                default -> List.of("concurrency", "design tradeoffs", "performance optimization", "clean API design");
            };
        };
    }

    private List<String> forbiddenCapabilities(TechnologySkill technology) {
        List<String> common = new ArrayList<>(List.of(
                "file IO",
                "network IO",
                "databases",
                "external services",
                "external processes",
                "unsupported dependencies"
        ));
        if (technology == TechnologySkill.JAVA) {
            common.add("non-JUnit test frameworks");
            common.add("reflection-heavy tasks");
        }
        if (technology == TechnologySkill.ANGULAR || technology == TechnologySkill.REACT) {
            common.add("new package installation");
            common.add("browser APIs requiring unavailable services");
        }
        return common;
    }

    private List<String> requiredQuestionElements(TechnologySkill technology) {
        return switch (technology) {
            case JAVA -> List.of("problem statement comment", "single runnable Main class", "org.junit.Assert validation checks from main", "hidden referenceSolution", "expected time and space complexity");
            case PYTHON -> List.of("problem statement comment", "runnable assert checks", "standard library only", "hidden referenceSolution", "expected time and space complexity");
            case ANGULAR -> List.of("problem statement comment inside editable source", "template-compatible task", "no new dependency requirement", "clear acceptance criteria");
            case REACT -> List.of("problem statement comment inside editable source", "component-compatible task", "no new dependency requirement", "clear acceptance criteria");
            default -> List.of("problem statement", "validation criteria", "sandbox-safe implementation scope");
        };
    }

    private String sandboxRules(TechnologySkill technology) {
        return switch (technology) {
            case JAVA -> "Java 17 only. Single source execution. Use org.junit.Assert assertions from main for validation. No file IO, network IO, databases, external processes, or external dependencies.";
            case PYTHON -> "Python standard library only. Include runnable assert statements for validation. No file IO, network IO, databases, external processes, or external packages.";
            case ANGULAR -> "Angular source edits only under src/app. No new dependencies, file IO, network IO, or browser APIs that require unavailable services.";
            case REACT -> "React source edits only under src. Only .tsx, .ts, and .css are editable. No new dependencies, file IO, network IO, or unavailable browser services.";
            default -> "Generate only tasks that can execute in the configured sandbox without external systems.";
        };
    }

    private String evaluationRubric(InterviewSession session, int difficultyLevel) {
        int years = session.getYearsOfExperience() == null ? 0 : session.getYearsOfExperience();
        String seniorGuidance = years >= 6 || difficultyLevel >= 4
                ? "For senior-level evaluation, code quality, complexity, API clarity, and edge-case reasoning carry stronger weight."
                : "For junior/mid-level evaluation, correctness and basic edge-case handling carry stronger weight.";
        return """
                Evaluation rubric v1:
                Correctness 35%%, edge cases 20%%, code quality 15%%, efficiency 15%%, time taken 10%%, execution attempts/confidence 5%%.
                Non-negotiable: runnable solution, no unsupported APIs, no validation-test tampering, and meaningful attempt at the requested problem.
                Execution attempts are a soft confidence signal because the IDE has no debugger.
                %s
                """.formatted(seniorGuidance);
    }

    private int idealDurationMinutes(int difficultyLevel, int timeRemainingSeconds, Integer yearsOfExperience) {
        int duration = switch (normalizeDifficulty(difficultyLevel)) {
            case 1 -> 8;
            case 2 -> 10;
            case 3 -> 12;
            case 4 -> 15;
            default -> 18;
        };
        int years = yearsOfExperience == null ? 0 : yearsOfExperience;
        if (years >= 8) {
            duration = Math.max(6, duration - 1);
        } else if (years <= 1) {
            duration += 2;
        }
        if (timeRemainingSeconds > 0 && timeRemainingSeconds < duration * 60) {
            return Math.max(5, timeRemainingSeconds / 60);
        }
        return duration;
    }

    private List<String> previousConcepts(List<EditableCodeFileDto> previousQuestions) {
        if (previousQuestions == null || previousQuestions.isEmpty()) {
            return List.of();
        }
        Set<String> concepts = new LinkedHashSet<>();
        for (EditableCodeFileDto question : previousQuestions) {
            concepts.addAll(splitConcepts(question.getQuestionConcepts()));
        }
        return List.copyOf(concepts);
    }

    private List<String> avoidConcepts(List<String> previousConcepts, List<String> targetConcepts) {
        if (previousConcepts == null || previousConcepts.isEmpty()) {
            return List.of();
        }
        Set<String> avoid = new LinkedHashSet<>(previousConcepts);
        if (targetConcepts != null) {
            targetConcepts.forEach(avoid::remove);
        }
        return List.copyOf(avoid);
    }

    private List<String> splitConcepts(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private int questionNumberHint(EditableCodeFileDto question) {
        if (question == null || question.getDisplayName() == null) {
            return 1;
        }
        String digits = question.getDisplayName().replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private int normalizeDifficulty(Integer difficultyLevel) {
        if (difficultyLevel == null) {
            return 1;
        }
        return Math.max(1, Math.min(5, difficultyLevel));
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record QuestionPolicyPlan(
            String questionPolicy,
            String evaluationRubric,
            List<String> targetConcepts,
            List<String> previousConcepts,
            List<String> avoidConcepts,
            List<String> forbiddenCapabilities,
            List<String> requiredQuestionElements,
            String sandboxRules,
            Integer idealDurationMinutes
    ) {
    }

    public record EvaluationPolicy(
            String questionPolicy,
            String evaluationRubric,
            List<String> expectedConcepts,
            List<String> nonNegotiableSignals
    ) {
    }

    public record RecommendationPolicy(
            String recommendationPolicy,
            String evaluationRubric
    ) {
    }
}
