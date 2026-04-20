package com.altimetrik.interview.runner;

import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.runner.model.RunnerCompileResult;
import com.altimetrik.interview.runner.model.RunnerExecutionResult;

public interface LanguageRunner {

    boolean supports(ExecutionLanguage language);

    RunnerCompileResult compile(String sourceCode);

    RunnerExecutionResult execute(String sourceCode, long timeoutMs, long memoryLimitMb);
}
