package com.altimetrik.interview.ai.controller;

import com.altimetrik.interview.ai.config.AiProviderProperties;
import com.altimetrik.interview.ai.dto.AiProviderReadinessResponse;
import com.altimetrik.interview.ai.service.AiInterviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiServiceStatusController {

    private final AiProviderProperties properties;
    private final AiInterviewService aiInterviewService;

    public AiServiceStatusController(AiProviderProperties properties, AiInterviewService aiInterviewService) {
        this.properties = properties;
        this.aiInterviewService = aiInterviewService;
    }

    @GetMapping("/status")
    public Mono<Map<String, Object>> status() {
        return Mono.just(Map.of(
                "status", "READY",
                "provider", properties.getName(),
                "questionModel", properties.getQuestionModel(),
                "evaluationModel", properties.getEvaluationModel(),
                "recommendationModel", properties.getRecommendationModel(),
                "apiKeyConfigured", properties.activeApiKey() != null && !properties.activeApiKey().isBlank()
        ));
    }

    @GetMapping("/status/provider")
    public Mono<AiProviderReadinessResponse> providerStatus() {
        return aiInterviewService.checkProviderReadiness();
    }
}
