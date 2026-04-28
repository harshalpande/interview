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
                1600,
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
                Use variationSeed to choose a fresh problem shape; avoid common default prompts when another valid sandbox-ready question would fit.
                Do not repeat any previous question title, problem statement, function name, or core concept supplied in the request.
                Difficulty is a 1 to 5 scale: 1 is basic screening, 3 is solid working knowledge, 5 is advanced/expert.
                Avoid file IO, network IO, databases, external services, unsupported dependencies, or tasks that cannot execute in the sandbox.
                For Java, use Java 17 and a single public class Main only if starter code needs a runnable class.
                For Java, starterCode must include runnable tests using org.junit.Assert from main; do not use any other test framework.
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
