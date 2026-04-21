package com.altimetrik.interview.runner;

import com.altimetrik.interview.dto.EditableCodeFileDto;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.runner.model.FrontendBuildResult;

import java.util.List;

public interface FrontendRunner {

    boolean supports(ExecutionLanguage language);

    long defaultTimeoutMs();

    FrontendBuildResult build(List<EditableCodeFileDto> files, long timeoutMs);
}
