package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.AiInterviewRecommendationRequest;
import com.altimetrik.interview.dto.AiInterviewRecommendationResponse;
import com.altimetrik.interview.dto.AiProviderReadinessResponse;
import com.altimetrik.interview.dto.AiQuestionGenerationRequest;
import com.altimetrik.interview.dto.AiQuestionResponse;
import com.altimetrik.interview.dto.AiSolutionEvaluationRequest;
import com.altimetrik.interview.dto.AiSolutionEvaluationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInterviewClientService {

    @Qualifier("aiServiceRestClient")
    private final RestClient aiServiceRestClient;

    public AiQuestionResponse generateQuestion(AiQuestionGenerationRequest request) {
        return post("/ai/questions/generate", request, AiQuestionResponse.class);
    }

    public AiSolutionEvaluationResponse evaluateSolution(AiSolutionEvaluationRequest request) {
        return post("/ai/questions/evaluate", request, AiSolutionEvaluationResponse.class);
    }

    public AiInterviewRecommendationResponse recommend(AiInterviewRecommendationRequest request) {
        return post("/ai/interviews/recommendation", request, AiInterviewRecommendationResponse.class);
    }

    public AiProviderReadinessResponse checkProviderReadiness() {
        try {
            return Objects.requireNonNull(aiServiceRestClient.get()
                    .uri("/ai/status/provider")
                    .retrieve()
                    .body(AiProviderReadinessResponse.class), "AI provider readiness response was empty");
        } catch (RestClientException exception) {
            log.error("AI provider readiness check failed", exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI interviewer is temporarily unavailable. Please try again in a few minutes.");
        }
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        try {
            return Objects.requireNonNull(aiServiceRestClient.post()
                    .uri(path)
                    .body(request)
                    .retrieve()
                    .body(responseType), "AI service response was empty for " + path);
        } catch (RestClientException exception) {
            log.error("AI service request failed for {}", path, exception);
            if (exception instanceof RestClientResponseException responseException) {
                if (responseException.getStatusCode().value() == 429) {
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "AI provider quota or rate limit has been reached. Please check OpenAI billing/quota or try again later.");
                }
                if (responseException.getStatusCode().value() == 401 || responseException.getStatusCode().value() == 403) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "AI provider credentials are not valid. Please check the configured API key.");
                }
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service is unavailable.");
        }
    }
}
