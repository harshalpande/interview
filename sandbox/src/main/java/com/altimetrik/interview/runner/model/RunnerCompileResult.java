package com.altimetrik.interview.runner.model;

import java.util.Collections;
import java.util.List;

public class RunnerCompileResult {
    private final boolean success;
    private final List<String> errors;

    private RunnerCompileResult(boolean success, List<String> errors) {
        this.success = success;
        this.errors = errors != null ? errors : Collections.emptyList();
    }

    public static RunnerCompileResult success() {
        return new RunnerCompileResult(true, Collections.emptyList());
    }

    public static RunnerCompileResult failure(List<String> errors) {
        return new RunnerCompileResult(false, errors);
    }

    public boolean isSuccess() {
        return success;
    }

    public List<String> getErrors() {
        return errors;
    }
}
