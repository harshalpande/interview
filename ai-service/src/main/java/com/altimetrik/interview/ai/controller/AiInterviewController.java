package com.altimetrik.interview.ai.controller;

import com.altimetrik.interview.ai.dto.AiInterviewRecommendationRequest;
import com.altimetrik.interview.ai.dto.AiInterviewRecommendationResponse;
import com.altimetrik.interview.ai.dto.AiQuestionGenerationRequest;
import com.altimetrik.interview.ai.dto.AiQuestionResponse;
import com.altimetrik.interview.ai.dto.AiSolutionEvaluationRequest;
import com.altimetrik.interview.ai.dto.AiSolutionEvaluationResponse;
import com.altimetrik.interview.ai.service.AiInterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiInterviewController {

    private final AiInterviewService aiInterviewService;

    @PostMapping("/questions/generate")
    public Mono<AiQuestionResponse> generateQuestion(@Valid @RequestBody AiQuestionGenerationRequest request) {
        return aiInterviewService.generateQuestion(request);
    }

    @PostMapping("/questions/evaluate")
    public Mono<AiSolutionEvaluationResponse> evaluateSolution(@Valid @RequestBody AiSolutionEvaluationRequest request) {
        return aiInterviewService.evaluateSolution(request);
    }

    @PostMapping("/interviews/recommendation")
    public Mono<AiInterviewRecommendationResponse> recommend(@Valid @RequestBody AiInterviewRecommendationRequest request) {
        return aiInterviewService.recommend(request);
    }
}

