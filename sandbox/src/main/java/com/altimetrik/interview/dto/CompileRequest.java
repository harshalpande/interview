package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ExecutionLanguage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompileRequest {
    private String sourceCode;
    @Builder.Default
    private ExecutionLanguage language = ExecutionLanguage.JAVA;
}
