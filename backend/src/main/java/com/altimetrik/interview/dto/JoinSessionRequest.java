package com.altimetrik.interview.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinSessionRequest {
    @NotBlank
    private String name;

    @Email
    @NotBlank
    private String email;

    private String timeZone;
}
