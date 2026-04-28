package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderReadinessResponse {
    private boolean ready;
    private String provider;
    private String model;
    private String message;
}
