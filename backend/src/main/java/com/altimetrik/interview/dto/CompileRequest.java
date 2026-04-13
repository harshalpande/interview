package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Java code compilation.
 * Contains the Java source code to be compiled.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompileRequest {
    private String sourceCode;
}
