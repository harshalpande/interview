package com.altimetrik.interview.ai.service;

import com.altimetrik.interview.ai.config.AiProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiResponsesClient {

    private final WebClient openAiWebClient;
    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper;

    public <T> Mono<T> createJsonResponse(String model,
                                          String instructions,
                                          String input,
                                          int maxOutputTokens,
                                          ObjectNode responseFormat,
                                          Class<T> responseType) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI API key is not configured."));
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("instructions", instructions);
        request.put("input", input);
        request.put("max_output_tokens", maxOutputTokens);
        request.set("text", objectMapper.createObjectNode().set("format", responseFormat));

        return openAiWebClient.post()
                .uri("/responses")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .map(this::extractOutputText)
                .map(outputText -> parseOutput(outputText, responseType))
                .retryWhen(Retry.max(properties.getMaxRetries()).filter(this::isRetryableOpenAiError))
                .onErrorMap(this::mapOpenAiThrowable);
    }

    private String extractOutputText(JsonNode response) {
        JsonNode output = response.path("output");
        if (!output.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI response did not contain output.");
        }
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                JsonNode text = contentItem.get("text");
                if (text != null && text.isTextual()) {
                    return text.asText();
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI response did not contain output text.");
    }

    private <T> T parseOutput(String outputText, Class<T> responseType) {
        try {
            return objectMapper.readValue(outputText, responseType);
        } catch (Exception exception) {
            log.error("Unable to parse OpenAI JSON output as {}", responseType.getSimpleName(), exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI returned incomplete JSON. Please retry.");
        }
    }

    private ResponseStatusException mapOpenAiError(WebClientResponseException exception) {
        log.error("OpenAI request failed status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
        if (exception.getStatusCode().value() == 429) {
            return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "OpenAI quota or rate limit has been reached.");
        }
        if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI credentials were rejected.");
        }
        if (exception.getStatusCode().is5xxServerError()) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI is temporarily unavailable. Please try again.");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request failed.");
    }

    private boolean isRetryableOpenAiError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException exception) {
            return exception.getStatusCode().is5xxServerError();
        }
        if (throwable instanceof ResponseStatusException exception) {
            return exception.getStatusCode().is5xxServerError();
        }
        return false;
    }

    private Throwable mapOpenAiThrowable(Throwable throwable) {
        if (throwable instanceof ResponseStatusException) {
            return throwable;
        }
        if (throwable instanceof WebClientResponseException exception) {
            return mapOpenAiError(exception);
        }
        if (Exceptions.isRetryExhausted(throwable) && throwable.getCause() instanceof ResponseStatusException exception) {
            return exception;
        }
        if (Exceptions.isRetryExhausted(throwable) && throwable.getCause() instanceof WebClientResponseException exception) {
            return mapOpenAiError(exception);
        }
        return throwable;
    }
}
