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
import java.util.Objects;
import java.util.Set;

@Service
public class AiPolicyEngineService {

    private static final String BANYAN_FAMILY_PREFIX = "banyan-family:";
    private static final List<BanyanProblemFamily> BANYAN_PROBLEM_FAMILIES = List.of(
            new BanyanProblemFamily("inventory-stock-management", "Inventory / Stock Management", "stock availability, reorder rules, reservations, and warehouse summaries"),
            new BanyanProblemFamily("journey-route-trip-tracking", "Journey / Route / Trip Tracking", "trip segments, route progress, stops, fare/distance summaries, and validation rules"),
            new BanyanProblemFamily("membership-subscription-billing", "Membership / Subscription / Billing", "plans, upgrades, renewals, discounts, billing cycles, and eligibility checks"),
            new BanyanProblemFamily("obstacle-grid-movement-rules", "Obstacle / Movement / Grid Rules", "grid movement, blocked cells, path rules, scoring, and boundary handling"),
            new BanyanProblemFamily("scheduling-booking-calendar-slots", "Scheduling / Booking / Calendar Slots", "time slots, conflicts, capacity, availability, and booking rules"),
            new BanyanProblemFamily("ranking-leaderboard-scoring", "Ranking / Leaderboard / Scoring", "score aggregation, ranking ties, filters, and leaderboard summaries"),
            new BanyanProblemFamily("validation-policy-rules-engine", "Validation / Policy Rules Engine", "rule validation, eligibility, policy failures, severity, and final decisions"),
            new BanyanProblemFamily("order-cart-checkout", "Order / Cart / Checkout", "cart totals, item rules, checkout validation, tax/discount calculations, and order status"),
            new BanyanProblemFamily("banking-wallet-ledger", "Banking / Wallet / Ledger", "wallet balances, transactions, reversals, ledger summaries, and consistency checks"),
            new BanyanProblemFamily("library-book-borrowing", "Library / Book Borrowing", "book availability, borrow/return rules, late fees, limits, and member status"),
            new BanyanProblemFamily("parking-lot-vehicle-slots", "Parking Lot / Vehicle Slots", "slot allocation, vehicle types, availability, fees, and release rules"),
            new BanyanProblemFamily("hotel-room-allocation", "Hotel / Room Allocation", "room availability, guest allocation, stay duration, pricing, and upgrade rules"),
            new BanyanProblemFamily("event-registration-waitlist", "Event Registration / Waitlist", "capacity, registrations, waitlists, cancellations, and attendee status"),
            new BanyanProblemFamily("ticketing-queue-management", "Ticketing / Queue Management", "ticket priority, queues, assignment, resolution order, and SLA-like rules"),
            new BanyanProblemFamily("attendance-leave-tracking", "Attendance / Leave Tracking", "daily presence, leave balances, approvals, penalties, and monthly summaries"),
            new BanyanProblemFamily("delivery-shipment-tracking", "Delivery / Shipment Tracking", "shipment states, route checkpoints, delays, delivery attempts, and status summaries"),
            new BanyanProblemFamily("food-ordering-restaurant-table-flow", "Food Ordering / Restaurant Table Flow", "orders, table capacity, menu availability, billing, and kitchen status"),
            new BanyanProblemFamily("hospital-appointment-triage", "Hospital / Appointment Triage", "patient priority, appointment slots, doctor availability, and triage decisions"),
            new BanyanProblemFamily("exam-marks-grade-processing", "Exam / Marks / Grade Processing", "marks, grades, pass rules, revaluation, ranks, and subject summaries"),
            new BanyanProblemFamily("course-student-enrollment", "Course / Student Enrollment", "course capacity, prerequisites, enrollment status, waitlists, and credit limits"),
            new BanyanProblemFamily("employee-shift-planning", "Employee Shift Planning", "shift assignment, coverage rules, conflicts, availability, and overtime checks"),
            new BanyanProblemFamily("coupon-discount-rules", "Coupon / Discount Rules", "coupon eligibility, stacking rules, thresholds, exclusions, and final payable amount"),
            new BanyanProblemFamily("notification-preference-rules", "Notification Preference Rules", "channel preferences, opt-outs, priority, quiet hours, and delivery decisions"),
            new BanyanProblemFamily("expense-split-settlement", "Expense Split / Settlement", "shared expenses, balances, settlements, rounding, and payer summaries"),
            new BanyanProblemFamily("device-health-sensor-alerts", "Device Health / Sensor Alerts", "sensor readings, thresholds, alert levels, recovery, and health summaries"),
            new BanyanProblemFamily("resource-capacity-planning", "Resource Capacity Planning", "resource allocation, capacity limits, utilization, conflicts, and planning summaries")
    );

    public QuestionPolicyPlan questionPolicy(InterviewSession session,
                                             int difficultyLevel,
                                             int questionNumber,
                                             List<EditableCodeFileDto> previousQuestions,
                                             int timeRemainingSeconds) {
        TechnologySkill technology = session.getTechnology() == null ? TechnologySkill.JAVA : session.getTechnology();
        boolean banyanStyle = session.getEvaluationStyle() == EvaluationStyle.BANYAN;
        BanyanProblemFamily banyanFamily = banyanStyle ? resolveBanyanFamily(session, previousQuestions) : null;
        List<String> targetConcepts = banyanStyle
                ? banyanTargetConcepts(technology, difficultyLevel, questionNumber, banyanFamily)
                : targetConcepts(technology, difficultyLevel, questionNumber);
        List<String> previousConcepts = previousConcepts(previousQuestions);
        List<String> avoidConcepts = avoidConcepts(previousConcepts, targetConcepts);
        List<String> forbiddenCapabilities = forbiddenCapabilities(technology);
        List<String> requiredElements = requiredQuestionElements(technology);
        int idealDurationMinutes = idealDurationMinutes(difficultyLevel, timeRemainingSeconds, session.getYearsOfExperience());
        String sandboxRules = sandboxRules(technology);
        String rubric = evaluationRubric(session, difficultyLevel);
        String experienceScope = experienceScopeGuidance(session, difficultyLevel, questionNumber, banyanStyle);
        String styleGuidance = banyanStyle
                ? """
                  Evaluation style: Banyan Style. Generate one evolving challenge level in the same single file.
                  Locked Banyan problem family: %s (%s). Family marker: %s%s.
                  Level 1 must start from this family and should not default to generic string/array/frequency-counting unless the family naturally requires it.
                  Level 1 must be a foothold, not the complete domain problem. It should introduce only the smallest useful part of the family.
                  Level 2+ must extend the exact same family, business domain, classes/methods, data model, and accepted candidate implementation. Add one new requirement and new validation assertions without changing the family.
                  Do not generate a separate, unrelated, or generic collection/top-k conversion unless that is a natural next rule inside the locked family.
                  """.formatted(
                        banyanFamily.title(),
                        banyanFamily.description(),
                        BANYAN_FAMILY_PREFIX,
                        banyanFamily.key()
                )
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
                Experience and level scope: %s.
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
                idealDurationMinutes,
                experienceScope
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

    private List<String> banyanTargetConcepts(TechnologySkill technology,
                                              int difficultyLevel,
                                              int questionNumber,
                                              BanyanProblemFamily family) {
        List<String> concepts = new ArrayList<>();
        concepts.add(BANYAN_FAMILY_PREFIX + family.key());
        concepts.add(family.title());
        concepts.add(family.description());
        concepts.addAll(targetConcepts(technology, difficultyLevel, questionNumber));
        concepts.add("evolving single challenge");
        concepts.add("preserve previous Banyan requirements");
        concepts.add("append level-specific validation assertions");
        return List.copyOf(concepts);
    }

    private BanyanProblemFamily resolveBanyanFamily(InterviewSession session, List<EditableCodeFileDto> previousQuestions) {
        String existingFamily = previousQuestions == null ? null : previousQuestions.stream()
                .flatMap(question -> splitConcepts(question.getQuestionConcepts()).stream())
                .filter(concept -> concept.startsWith(BANYAN_FAMILY_PREFIX))
                .map(concept -> concept.substring(BANYAN_FAMILY_PREFIX.length()).trim())
                .filter(concept -> !concept.isBlank())
                .findFirst()
                .orElse(null);
        if (existingFamily != null) {
            return BANYAN_PROBLEM_FAMILIES.stream()
                    .filter(family -> family.key().equals(existingFamily))
                    .findFirst()
                    .orElse(BANYAN_PROBLEM_FAMILIES.get(0));
        }

        String seed = String.join("|",
                valueOrDefault(session.getId(), "unspecified-session"),
                session.getTechnology() == null ? TechnologySkill.JAVA.name() : session.getTechnology().name(),
                valueOrDefault(session.getTargetRole(), "unspecified"),
                String.valueOf(session.getYearsOfExperience() == null ? 0 : session.getYearsOfExperience()),
                String.valueOf(session.getStartingDifficultyLevel() == null ? 1 : session.getStartingDifficultyLevel())
        );
        int index = Math.floorMod(Objects.hash(seed), BANYAN_PROBLEM_FAMILIES.size());
        return BANYAN_PROBLEM_FAMILIES.get(index);
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
        ExperienceBand experienceBand = experienceBand(session);
        String seniorGuidance = experienceBand.ordinal() >= ExperienceBand.SEVEN_TO_TEN.ordinal() || difficultyLevel >= 4
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

    private String experienceScopeGuidance(InterviewSession session,
                                           int difficultyLevel,
                                           int questionNumber,
                                           boolean banyanStyle) {
        ExperienceBand band = experienceBand(session);
        if (!banyanStyle) {
            return switch (band) {
                case ONE_TO_THREE -> "1-3 year profile: keep the question direct, with one primary concept and a small implementation surface.";
                case FOUR_TO_SIX -> "4-6 year profile: use practical rules and edge cases, but keep the implementation narrow and time-boxed.";
                case SEVEN_TO_TEN -> "7-10 year profile: include design judgement or efficient data handling, without turning it into a broad system exercise.";
                case ELEVEN_TO_FIFTEEN -> "11-15 year profile: allow richer tradeoffs and API clarity checks, while keeping the task executable in one file.";
                case SIXTEEN_TO_TWENTY -> "16-20 year profile: test architecture-minded code structure and edge-case discipline within a focused sandbox task.";
                case TWENTY_PLUS -> "20+ year profile: test clarity, simplification, and tradeoff judgement, not unnecessary breadth.";
            };
        }
        return banyanExperienceScope(band, normalizeDifficulty(difficultyLevel), questionNumber);
    }

    private String banyanExperienceScope(ExperienceBand band, int difficultyLevel, int questionNumber) {
        String deterministicRule = "Complexity must increase deterministically by level: each new level adds exactly one new requirement and matching assertions. Do not add multiple independent features in one level.";
        if (questionNumber <= 1) {
            return deterministicRule + " " + switch (band) {
                case ONE_TO_THREE -> "Banyan Level 1 for 1-3 years: start with one small intentional bug or one focused missing method. Use no model or one very small model, 3 to 5 assertions, one primary concept, and one business rule only. Avoid date math, money rules, sorting, aggregation, multiple models, or combined eligibility/discount/final-total logic.";
                case FOUR_TO_SIX -> "Banyan Level 1 for 4-6 years: use one small model and one method, or two tightly related methods, with 4 to 6 assertions and one edge rule. Save collections-heavy processing and multi-rule decisions for later levels.";
                case SEVEN_TO_TEN -> "Banyan Level 1 for 7-10 years: establish a modest domain base with one or two models and at most two related methods. Include clear edge cases, but do not include the full final workflow in Level 1.";
                case ELEVEN_TO_FIFTEEN -> "Banyan Level 1 for 11-15 years: allow a slightly richer base API, but keep it as the trunk of the challenge with at most two related operations. Save policy combinations, ranking, summaries, or optimization for later levels.";
                case SIXTEEN_TO_TWENTY -> "Banyan Level 1 for 16-20 years: test clean modeling and simple rule implementation, not breadth. Keep the first level compact so later levels can add design pressure.";
                case TWENTY_PLUS -> "Banyan Level 1 for 20+ years: test simplification, naming, and clean base design. Do not overload Level 1; use later levels for tradeoffs and richer constraints.";
            };
        }
        if (difficultyLevel <= 2) {
            return deterministicRule + " " + switch (band) {
                case ONE_TO_THREE -> "For 1-3 years, add one small method or one extra branch in the existing method. Keep assertions simple and avoid introducing advanced APIs.";
                case FOUR_TO_SIX -> "For 4-6 years, add one practical rule or one collection-based helper. Keep the data model stable.";
                case SEVEN_TO_TEN -> "For 7-10 years, add one collection, map, or sorting rule only if it naturally follows the existing domain.";
                case ELEVEN_TO_FIFTEEN -> "For 11-15 years, add one policy or edge-case dimension, not a second workflow.";
                case SIXTEEN_TO_TWENTY, TWENTY_PLUS -> "For high-experience profiles, add one design-pressure rule at a time and keep the challenge solvable in the editor.";
            };
        }
        return deterministicRule + " " + switch (band) {
            case ONE_TO_THREE -> "For 1-3 years, Level 3+ may introduce simple collections or maps, but only after earlier levels are passed.";
            case FOUR_TO_SIX -> "For 4-6 years, Level 3+ may introduce sorting, grouping, or a small summary calculation.";
            case SEVEN_TO_TEN -> "For 7-10 years, Level 3+ may introduce streams/lambdas or efficient data-structure choices when appropriate.";
            case ELEVEN_TO_FIFTEEN -> "For 11-15 years, Level 3+ may introduce tradeoffs, policy composition, or cleaner API expectations.";
            case SIXTEEN_TO_TWENTY -> "For 16-20 years, Level 3+ may introduce maintainability, extensibility, or performance constraints, one at a time.";
            case TWENTY_PLUS -> "For 20+ years, Level 3+ may test simplifying a richer rule set while still keeping each level bounded.";
        };
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

    private ExperienceBand experienceBand(InterviewSession session) {
        int years = session.getYearsOfExperience() == null ? 0 : session.getYearsOfExperience();
        String role = valueOrDefault(session.getTargetRole(), "").toLowerCase(Locale.ROOT);
        if (role.contains("junior") || role.contains("associate") || role.contains("entry")) {
            years = Math.min(years, 3);
        }
        if (years <= 3) {
            return ExperienceBand.ONE_TO_THREE;
        }
        if (years <= 6) {
            return ExperienceBand.FOUR_TO_SIX;
        }
        if (years <= 10) {
            return ExperienceBand.SEVEN_TO_TEN;
        }
        if (years <= 15) {
            return ExperienceBand.ELEVEN_TO_FIFTEEN;
        }
        if (years <= 20) {
            return ExperienceBand.SIXTEEN_TO_TWENTY;
        }
        return ExperienceBand.TWENTY_PLUS;
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

    private record BanyanProblemFamily(String key, String title, String description) {
    }

    private enum ExperienceBand {
        ONE_TO_THREE,
        FOUR_TO_SIX,
        SEVEN_TO_TEN,
        ELEVEN_TO_FIFTEEN,
        SIXTEEN_TO_TWENTY,
        TWENTY_PLUS
    }
}
