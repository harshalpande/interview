package com.altimetrik.interview.ai.service;

import com.altimetrik.interview.ai.config.AiProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiGenerateContentClient {

    private final WebClient.Builder webClientBuilder;
    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper;

    public <T> Mono<T> createJsonResponse(String model,
                                          String instructions,
                                          String input,
                                          int maxOutputTokens,
                                          ObjectNode responseFormat,
                                          Class<T> responseType) {
        if (properties.getGeminiApiKey() == null || properties.getGeminiApiKey().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API key is not configured."));
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.set("system_instruction", contentFromText(instructions));
        ArrayNode contents = request.putArray("contents");
        contents.add(contentFromText(input));

        ObjectNode generationConfig = objectMapper.createObjectNode();
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseSchema", toGeminiSchema(responseFormat));
        request.set("generationConfig", generationConfig);

        WebClient geminiWebClient = webClientBuilder
                .baseUrl(properties.getGeminiBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-goog-api-key", properties.getGeminiApiKey())
                .build();

        return geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .build(normalizeModelName(model)))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .map(this::extractOutputText)
                .map(outputText -> parseOutput(outputText, responseType))
                .retryWhen(Retry.max(properties.getMaxRetries()).filter(this::isRetryableGeminiError))
                .onErrorMap(this::mapGeminiThrowable);
    }

    private ObjectNode contentFromText(String text) {
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", text == null ? "" : text);
        return content;
    }

    private String normalizeModelName(String model) {
        if (model == null || model.isBlank()) {
            return "gemini-2.5-flash";
        }
        String trimmed = model.trim();
        return trimmed.startsWith("models/") ? trimmed.substring("models/".length()) : trimmed;
    }

    private JsonNode toGeminiSchema(ObjectNode responseFormat) {
        JsonNode schema = responseFormat.has("schema") ? responseFormat.get("schema") : responseFormat;
        return sanitizeSchema(schema);
    }

    private JsonNode sanitizeSchema(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                array.add(sanitizeSchema(item));
            }
            return array;
        }
        if (!node.isObject()) {
            return node.deepCopy();
        }

        ObjectNode sanitized = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            if ("additionalProperties".equals(name)) {
                continue;
            }
            if ("type".equals(name) && field.getValue().isTextual()) {
                sanitized.put(name, field.getValue().asText().toUpperCase());
            } else {
                sanitized.set(name, sanitizeSchema(field.getValue()));
            }
        }
        return sanitized;
    }

    private String extractOutputText(JsonNode response) {
        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini response did not contain candidates.");
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini response did not contain content parts.");
        }
        for (JsonNode part : parts) {
            JsonNode text = part.get("text");
            if (text != null && text.isTextual()) {
                return text.asText();
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini response did not contain output text.");
    }

    private <T> T parseOutput(String outputText, Class<T> responseType) {
        String jsonText = extractJsonPayload(outputText);
        try {
            return objectMapper.readValue(jsonText, responseType);
        } catch (Exception exception) {
            log.error("Unable to parse Gemini JSON output as {}. Output preview={}", responseType.getSimpleName(), preview(outputText), exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini response was not valid JSON for this operation.");
        }
    }

    private String extractJsonPayload(String outputText) {
        if (outputText == null) {
            return "";
        }
        String trimmed = outputText.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int closingFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && closingFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, closingFence).trim();
            }
        }
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return trimmed;
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500) + "...";
    }

    private boolean isRetryableGeminiError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException exception) {
            return exception.getStatusCode().is5xxServerError();
        }
        return false;
    }

    private ResponseStatusException mapGeminiError(WebClientResponseException exception) {
        log.error("Gemini request failed status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
        if (exception.getStatusCode().value() == 429) {
            return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Gemini quota or rate limit has been reached.");
        }
        if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini credentials were rejected.");
        }
        if (exception.getStatusCode().value() == 400) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request was rejected. Check the configured model and request format.");
        }
        if (exception.getStatusCode().is5xxServerError()) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini is temporarily unavailable. Please try again.");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed.");
    }

    private Throwable mapGeminiThrowable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException exception) {
            return mapGeminiError(exception);
        }
        if (Exceptions.isRetryExhausted(throwable) && throwable.getCause() instanceof WebClientResponseException exception) {
            return mapGeminiError(exception);
        }
        return throwable;
    }
}
