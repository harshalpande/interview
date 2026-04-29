package com.altimetrik.interview.ai.dto;

public record AiProviderReadinessResponse(
        boolean ready,
        String provider,
        String model,
        String message
) {
}
