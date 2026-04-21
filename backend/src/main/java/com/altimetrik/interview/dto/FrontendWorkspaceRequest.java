package com.altimetrik.interview.dto;

import com.altimetrik.interview.enums.ExecutionLanguage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrontendWorkspaceRequest {
    private String sessionId;
    @Builder.Default
    private ExecutionLanguage language = ExecutionLanguage.ANGULAR;
    private List<EditableCodeFileDto> files;
}
