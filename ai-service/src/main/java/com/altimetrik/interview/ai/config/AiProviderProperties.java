package com.altimetrik.interview.ai.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "ai.provider")
public class AiProviderProperties {

    @NotBlank
    private String name = "openai";

    private String apiKey;

    private String baseUrl = "https://api.openai.com/v1";

    private String geminiApiKey;

    private String geminiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";

    private String questionModel = "gpt-4.1-mini";

    private String evaluationModel = "gpt-4.1-mini";

    private String recommendationModel = "gpt-4.1-mini";

    private String openaiQuestionModel = "gpt-4.1-mini";

    private String openaiEvaluationModel = "gpt-4.1-mini";

    private String openaiRecommendationModel = "gpt-4.1-mini";

    private String geminiQuestionModel = "gemini-2.5-flash";

    private String geminiEvaluationModel = "gemini-2.5-flash";

    private String geminiRecommendationModel = "gemini-2.5-flash";

    @Min(5)
    private int requestTimeoutSeconds = 60;

    @Min(0)
    private int maxRetries = 2;

    public boolean isGeminiProvider() {
        return "gemini".equalsIgnoreCase(name);
    }

    public boolean isOpenAiProvider() {
        return "openai".equalsIgnoreCase(name);
    }

    public String activeApiKey() {
        if (isGeminiProvider()) {
            return geminiApiKey;
        }
        return apiKey;
    }

    public String getQuestionModel() {
        if (isGeminiProvider()) {
            return firstNonBlank(geminiQuestionModel, questionModel, "gemini-2.5-flash");
        }
        return firstNonBlank(openaiQuestionModel, questionModel, "gpt-4.1-mini");
    }

    public String getEvaluationModel() {
        if (isGeminiProvider()) {
            return firstNonBlank(geminiEvaluationModel, evaluationModel, "gemini-2.5-flash");
        }
        return firstNonBlank(openaiEvaluationModel, evaluationModel, "gpt-4.1-mini");
    }

    public String getRecommendationModel() {
        if (isGeminiProvider()) {
            return firstNonBlank(geminiRecommendationModel, recommendationModel, "gemini-2.5-flash");
        }
        return firstNonBlank(openaiRecommendationModel, recommendationModel, "gpt-4.1-mini");
    }

    private String firstNonBlank(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary.trim();
        }
        return fallback;
    }
}
