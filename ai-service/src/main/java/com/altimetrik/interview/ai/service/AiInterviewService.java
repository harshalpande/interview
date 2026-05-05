package com.altimetrik.interview.ai.service;

import com.altimetrik.interview.ai.config.AiProviderProperties;
import com.altimetrik.interview.ai.dto.AiInterviewRecommendationRequest;
import com.altimetrik.interview.ai.dto.AiInterviewRecommendationResponse;
import com.altimetrik.interview.ai.dto.AiProviderReadinessResponse;
import com.altimetrik.interview.ai.dto.AiQuestionGenerationRequest;
import com.altimetrik.interview.ai.dto.AiQuestionResponse;
import com.altimetrik.interview.ai.dto.AiSolutionEvaluationRequest;
import com.altimetrik.interview.ai.dto.AiSolutionEvaluationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiInterviewService {

    private final OpenAiResponsesClient openAiResponsesClient;
    private final GeminiGenerateContentClient geminiGenerateContentClient;
    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper;

    public Mono<AiQuestionResponse> generateQuestion(AiQuestionGenerationRequest request) {
        return activeClient().createJsonResponse(
                properties.getQuestionModel(),
                questionInstructions(),
                toJson(request),
                questionMaxOutputTokens(request),
                questionResponseFormat(),
                AiQuestionResponse.class
        );
    }

    public Mono<AiSolutionEvaluationResponse> evaluateSolution(AiSolutionEvaluationRequest request) {
        return activeClient().createJsonResponse(
                properties.getEvaluationModel(),
                evaluationInstructions(),
                toJson(request),
                1400,
                evaluationResponseFormat(),
                AiSolutionEvaluationResponse.class
        );
    }

    public Mono<AiInterviewRecommendationResponse> recommend(AiInterviewRecommendationRequest request) {
        return activeClient().createJsonResponse(
                properties.getRecommendationModel(),
                recommendationInstructions(),
                toJson(request),
                1400,
                recommendationResponseFormat(),
                AiInterviewRecommendationResponse.class
        );
    }

    public Mono<AiProviderReadinessResponse> checkProviderReadiness() {
        String provider = properties.getName();
        String model = properties.getQuestionModel();
        if (properties.activeApiKey() == null || properties.activeApiKey().isBlank()) {
            return Mono.just(new AiProviderReadinessResponse(false, provider, model,
                    "AI interviewer is not configured. Please contact the administrator."));
        }

        return activeClient().createJsonResponse(
                        model,
                        """
                        Return a tiny JSON readiness confirmation for an interview question generation service.
                        Do not generate an interview question.
                        JSON shape:
                        {
                          "status": "READY"
                        }
                        """,
                        "{\"check\":\"provider-readiness\"}",
                        80,
                        readinessResponseFormat(),
                        ReadinessProbe.class
                )
                .map(response -> new AiProviderReadinessResponse(true, provider, model,
                        "AI interviewer is ready."))
                .onErrorResume(error -> Mono.just(new AiProviderReadinessResponse(false, provider, model,
                        providerUnavailableMessage(provider, error))));
    }

    private AiJsonResponseClient activeClient() {
        if (properties.isGeminiProvider()) {
            return geminiGenerateContentClient::createJsonResponse;
        }
        return openAiResponsesClient::createJsonResponse;
    }

    private int questionMaxOutputTokens(AiQuestionGenerationRequest request) {
        if (request == null) {
            return properties.getQuestionMaxOutputTokens();
        }
        return "BANYAN".equalsIgnoreCase(request.evaluationStyle())
                ? banyanQuestionMaxOutputTokens(request)
                : standardQuestionMaxOutputTokens(request);
    }

    private int standardQuestionMaxOutputTokens(AiQuestionGenerationRequest request) {
        int difficulty = normalizeLevel(request.currentDifficulty(), request.questionNumber());
        ExperienceBand band = experienceBand(request.yearsOfExperience(), request.targetRole());
        int estimate = 3200 + (difficulty * 350);
        if (band.ordinal() >= ExperienceBand.SEVEN_TO_TEN.ordinal()) {
            estimate += 400;
        }
        return clamp(estimate, 3000, properties.getQuestionMaxOutputTokens());
    }

    private int banyanQuestionMaxOutputTokens(AiQuestionGenerationRequest request) {
        int level = request.banyanLevel() == null ? normalizeLevel(request.currentDifficulty(), request.questionNumber()) : request.banyanLevel();
        ExperienceBand band = experienceBand(request.yearsOfExperience(), request.targetRole());
        int estimate = banyanBaseTokenBudget(band, level);
        estimate += Math.min(3500, estimatedTokens(request.previousBanyanChallenge()) / 2);
        return clamp(estimate, 3500, properties.getBanyanQuestionMaxOutputTokens());
    }

    private int banyanBaseTokenBudget(ExperienceBand band, int level) {
        if (level <= 1) {
            return switch (band) {
                case ONE_TO_THREE -> 4200;
                case FOUR_TO_SIX -> 4800;
                case SEVEN_TO_TEN -> 5400;
                case ELEVEN_TO_FIFTEEN -> 5800;
                case SIXTEEN_TO_TWENTY -> 6000;
                case TWENTY_PLUS -> 6200;
            };
        }
        if (level == 2) {
            return switch (band) {
                case ONE_TO_THREE -> 5600;
                case FOUR_TO_SIX -> 6400;
                case SEVEN_TO_TEN -> 7200;
                case ELEVEN_TO_FIFTEEN -> 8000;
                case SIXTEEN_TO_TWENTY -> 8400;
                case TWENTY_PLUS -> 8600;
            };
        }
        return switch (band) {
            case ONE_TO_THREE -> 6800;
            case FOUR_TO_SIX -> 7800;
            case SEVEN_TO_TEN -> 9000;
            case ELEVEN_TO_FIFTEEN -> 9800;
            case SIXTEEN_TO_TWENTY -> 10400;
            case TWENTY_PLUS -> 10800;
        };
    }

    private int normalizeLevel(String level, Integer fallback) {
        if (level != null) {
            String digits = level.replaceAll("\\D+", "");
            if (!digits.isBlank()) {
                try {
                    return clamp(Integer.parseInt(digits), 1, 5);
                } catch (NumberFormatException ignored) {
                    // Fall through to fallback.
                }
            }
        }
        return fallback == null ? 1 : clamp(fallback, 1, 5);
    }

    private int estimatedTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(1, value.length() / 4);
    }

    private ExperienceBand experienceBand(Integer yearsOfExperience, String targetRole) {
        int years = yearsOfExperience == null ? 0 : yearsOfExperience;
        if (targetRole != null) {
            String role = targetRole.toLowerCase();
            if (role.contains("junior") || role.contains("associate") || role.contains("entry")) {
                years = Math.min(years, 3);
            }
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum ExperienceBand {
        ONE_TO_THREE,
        FOUR_TO_SIX,
        SEVEN_TO_TEN,
        ELEVEN_TO_FIFTEEN,
        SIXTEEN_TO_TWENTY,
        TWENTY_PLUS
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to serialize AI request", exception);
        }
    }

    private String questionInstructions() {
        return """
                You are an AI technical interviewer for a coding interview platform.
                Generate one practical coding question that can run in the platform sandbox.
                Treat questionPolicy, requiredQuestionElements, forbiddenCapabilities, targetConcepts, evaluationRubric, and sandboxRules from the request as authoritative backend rules.
                Respect the supplied technology, experience, target role, numeric difficulty level, ideal duration, time left, previous concepts, previous question history, variationSeed, and sandbox rules.
                If evaluationStyle is BANYAN, generate an evolving single-challenge level, not a new independent question.
                For BANYAN level 1, create a base problem that can naturally grow through later requirements, preferably with a small existing class/model and one bug or missing method.
                For BANYAN, apply deterministic experience bands from yearsOfExperience: 1-3, 4-6, 7-10, 11-15, 16-20, and 20+ years. Complexity may increase with the band and level, but it must never jump to a broad multi-feature problem in the first level.
                For BANYAN level 1 and 1-3 year candidates, keep scope deliberately small: one intentional bug or one method to fix/implement, 3 to 5 validation assertions, one primary concept, and at most one simple model. Do not combine date calculation, eligibility, discount, upgrade, billing amount, sorting, and aggregation in the first level.
                For BANYAN level 1 and 4-6 year candidates, use one small model and one method, or two tightly related methods, with one edge rule.
                For BANYAN level 1 and 7+ year candidates, modest domain modeling is acceptable, but Level 1 must still be only the base foothold. Save policy combinations, ranking, summaries, optimization, and tradeoffs for later levels.
                For BANYAN level 2, add exactly one new requirement or method. For BANYAN level 3+, add one measured complication at a time such as collection processing, sorting/tie-breaking, streams/lambdas for Java, or richer edge cases.
                For BANYAN level 2 or above, previousBanyanChallenge is authoritative context. Extend the exact same business domain, classes, method names, data model, and candidate implementation shape with one additional requirement.
                For BANYAN level 2 or above, starterCode must preserve all previous problem statements, all previous validation assertions, and the candidate's existing accepted code as much as possible. Add the new requirement below the previous requirements and append only the new validation assertions.
                For BANYAN, never switch to an unrelated problem, unrelated function name, unrelated class model, or separate tab/question concept. The filePath should remain Banyan.java for Java or banyan.py for Python.
                For BANYAN, displayName should be "Banyan Level N", title should mention the evolving challenge, and starterCode must contain all accumulated level requirements visible in comments plus all accumulated assertions.
                For BANYAN, keep starterCode and referenceSolution concise. Preserve behavior and assertions, but avoid long explanations, excessive helper classes, or unnecessary boilerplate.
                For all technologies, problemStatement must be plain text only. Do not wrap problemStatement in Java block comments, Python triple-quoted strings, or any other language comment delimiter.
                If starterCode includes a problem statement comment, include it only once. Do not duplicate the same problem statement in both nested comment blocks.
                Use variationSeed to choose a fresh problem shape; avoid common default prompts when another valid sandbox-ready question would fit.
                For non-BANYAN requests, do not repeat any previous question title, problem statement, function name, or core concept supplied in the request.
                Difficulty is a 1 to 5 scale: 1 is basic screening, 3 is solid working knowledge, 5 is advanced/expert.
                Avoid file IO, network IO, databases, external services, unsupported dependencies, or tasks that cannot execute in the sandbox.
                For Java, use Java 17 and a single public class Main only if starter code needs a runnable class.
                For Java, starterCode must include runnable tests using org.junit.Assert from main; do not use any other test framework.
                For Java, starterCode and referenceSolution must print System.out.println("All Assertions are completed"); after all validation assertions pass.
                For Java, referenceSolution must be a complete runnable public class Main containing the full implementation and the same org.junit.Assert checks from starterCode.
                For Python, use standard library only.
                For Python, starterCode must include runnable assert statements from main or an equivalent function.
                For Python, referenceSolution must be complete runnable Python source containing the full implementation and the same assert checks from starterCode.
                For Angular and React, keep the task inside the existing editable source files and avoid adding dependencies.
                Do not include the full solution in starterCode. Put the full reference implementation only in referenceSolution.
                The backend will compile/run referenceSolution before showing the question; if any validation assertion is wrong, missing, or non-runnable, the question will be rejected.
                JSON shape:
                {
                  "title": "short title",
                  "filePath": "Question1.java or question-1.py or src/App.tsx",
                  "displayName": "Question 1",
                  "problemStatement": "candidate-facing problem statement with constraints and examples",
                  "starterCode": "starter code containing the problem as comments when useful",
                  "difficulty": "1",
                  "difficultyLevel": 1,
                  "idealDurationMinutes": 10,
                  "referenceSolution": "complete hidden reference implementation",
                  "expectedTimeComplexity": "O(n)",
                  "expectedSpaceComplexity": "O(1)",
                  "concepts": ["concept"],
                  "evaluationFocus": ["focus area"]
                }
                """;
    }

    private String evaluationInstructions() {
        return """
                You are an AI evaluator for a coding interview platform.
                Treat evaluationRubric, questionPolicy, expectedConcepts, and nonNegotiableSignals from the request as authoritative backend rules.
                Evaluate the submitted solution against the question, execution output, errors, time taken, and run attempts.
                Compare against the supplied hidden reference solution and expected complexity when available.
                If questionIntegrityNotes reports changed problem text or tests, mention it clearly and lower confidence or request human review.
                Be fair: this editor has no debugger, so execution attempts are a supporting signal, not a standalone failure.
                Scores are integers from 0 to 100. nextDifficultyLevel must be an integer from 1 to 5.
                verdict must be PASS, PARTIAL, FAIL, or NEEDS_HUMAN_REVIEW.
                JSON shape:
                {
                  "correctnessScore": 0,
                  "codeQualityScore": 0,
                  "edgeCaseScore": 0,
                  "efficiencyScore": 0,
                  "overallScore": 0,
                  "verdict": "PASS",
                  "nextDifficulty": "2",
                  "nextDifficultyLevel": 2,
                  "summary": "brief evaluation",
                  "complexityAssessment": "candidate time and space complexity assessment",
                  "questionIntegrityNotes": "whether original problem/tests appear unchanged",
                  "strengths": ["strength"],
                  "concerns": ["concern"]
                }
                """;
    }

    private String recommendationInstructions() {
        return """
                You are an AI interview panel assistant.
                Treat recommendationPolicy and evaluationRubric from the request as authoritative backend rules.
                Summarize the candidate's performance across all questions.
                Human review is mandatory, so your output is a recommendation, not the final decision.
                rating must be EXCELLENT, GOOD, FAIR, BAD, or DISQUALIFIED.
                recommendationDecision must be YES, NO, or REEVALUATION.
                JSON shape:
                {
                  "rating": "GOOD",
                  "recommendationDecision": "YES",
                  "overallScore": 0,
                  "summary": "brief hiring recommendation",
                  "strengths": ["strength"],
                  "risks": ["risk"],
                  "suggestedFollowUps": ["follow-up"],
                  "humanReviewRequired": true
                }
                """;
    }

    private ObjectNode questionResponseFormat() {
        ObjectNode propertiesNode = objectMapper.createObjectNode();
        addString(propertiesNode, "title");
        addString(propertiesNode, "filePath");
        addString(propertiesNode, "displayName");
        addString(propertiesNode, "problemStatement");
        addString(propertiesNode, "starterCode");
        addString(propertiesNode, "difficulty");
        addInteger(propertiesNode, "difficultyLevel");
        addInteger(propertiesNode, "idealDurationMinutes");
        addString(propertiesNode, "referenceSolution");
        addString(propertiesNode, "expectedTimeComplexity");
        addString(propertiesNode, "expectedSpaceComplexity");
        addStringArray(propertiesNode, "concepts");
        addStringArray(propertiesNode, "evaluationFocus");
        return schemaFormat("ai_question", propertiesNode,
                "title", "filePath", "displayName", "problemStatement", "starterCode", "difficulty", "difficultyLevel", "idealDurationMinutes", "referenceSolution", "expectedTimeComplexity", "expectedSpaceComplexity", "concepts", "evaluationFocus");
    }

    private ObjectNode evaluationResponseFormat() {
        ObjectNode propertiesNode = objectMapper.createObjectNode();
        addInteger(propertiesNode, "correctnessScore");
        addInteger(propertiesNode, "codeQualityScore");
        addInteger(propertiesNode, "edgeCaseScore");
        addInteger(propertiesNode, "efficiencyScore");
        addInteger(propertiesNode, "overallScore");
        addString(propertiesNode, "verdict");
        addString(propertiesNode, "nextDifficulty");
        addInteger(propertiesNode, "nextDifficultyLevel");
        addString(propertiesNode, "summary");
        addString(propertiesNode, "complexityAssessment");
        addString(propertiesNode, "questionIntegrityNotes");
        addStringArray(propertiesNode, "strengths");
        addStringArray(propertiesNode, "concerns");
        return schemaFormat("ai_evaluation", propertiesNode,
                "correctnessScore", "codeQualityScore", "edgeCaseScore", "efficiencyScore", "overallScore", "verdict", "nextDifficulty", "nextDifficultyLevel", "summary", "complexityAssessment", "questionIntegrityNotes", "strengths", "concerns");
    }

    private ObjectNode recommendationResponseFormat() {
        ObjectNode propertiesNode = objectMapper.createObjectNode();
        addString(propertiesNode, "rating");
        addString(propertiesNode, "recommendationDecision");
        addInteger(propertiesNode, "overallScore");
        addString(propertiesNode, "summary");
        addStringArray(propertiesNode, "strengths");
        addStringArray(propertiesNode, "risks");
        addStringArray(propertiesNode, "suggestedFollowUps");
        propertiesNode.set("humanReviewRequired", objectMapper.createObjectNode().put("type", "boolean"));
        return schemaFormat("ai_recommendation", propertiesNode,
                "rating", "recommendationDecision", "overallScore", "summary", "strengths", "risks", "suggestedFollowUps", "humanReviewRequired");
    }

    private ObjectNode readinessResponseFormat() {
        ObjectNode propertiesNode = objectMapper.createObjectNode();
        addString(propertiesNode, "status");
        return schemaFormat("ai_provider_readiness", propertiesNode, "status");
    }

    private ObjectNode schemaFormat(String name, ObjectNode propertiesNode, String... requiredFields) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", propertiesNode);
        schema.put("additionalProperties", false);
        ArrayNode required = objectMapper.createArrayNode();
        for (String field : requiredFields) {
            required.add(field);
        }
        schema.set("required", required);

        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_schema");
        format.put("name", name);
        format.put("strict", true);
        format.set("schema", schema);
        return format;
    }

    private void addString(ObjectNode propertiesNode, String fieldName) {
        propertiesNode.set(fieldName, objectMapper.createObjectNode().put("type", "string"));
    }

    private void addInteger(ObjectNode propertiesNode, String fieldName) {
        propertiesNode.set(fieldName, objectMapper.createObjectNode().put("type", "integer"));
    }

    private void addStringArray(ObjectNode propertiesNode, String fieldName) {
        ObjectNode arraySchema = objectMapper.createObjectNode();
        arraySchema.put("type", "array");
        arraySchema.set("items", objectMapper.createObjectNode().put("type", "string"));
        propertiesNode.set(fieldName, arraySchema);
    }

    private String providerUnavailableMessage(String provider, Throwable error) {
        String providerLabel = provider == null || provider.isBlank() ? "AI provider" : provider;
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        if (message.contains("quota") || message.contains("rate limit") || message.contains("429")) {
            return providerLabel + " is currently rate limited. Please try again later.";
        }
        if (message.contains("credential") || message.contains("401") || message.contains("403")) {
            return providerLabel + " credentials are not valid. Please contact the administrator.";
        }
        return providerLabel + " is temporarily unavailable. Please try again in a few minutes.";
    }

    private record ReadinessProbe(String status) {
    }

    @FunctionalInterface
    private interface AiJsonResponseClient {
        <T> Mono<T> createJsonResponse(String model,
                                       String instructions,
                                       String input,
                                       int maxOutputTokens,
                                       ObjectNode responseFormat,
                                       Class<T> responseType);
    }
}
