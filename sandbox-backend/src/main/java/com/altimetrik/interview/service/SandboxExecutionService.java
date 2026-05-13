package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.CompileRequest;
import com.altimetrik.interview.dto.CompileResponse;
import com.altimetrik.interview.dto.ExecuteRequest;
import com.altimetrik.interview.dto.ExecuteResponse;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.enums.ExecutionPriority;
import com.altimetrik.interview.runner.LanguageRunner;
import com.altimetrik.interview.runner.model.RunnerCompileResult;
import com.altimetrik.interview.runner.model.RunnerExecutionResult;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxExecutionService {

    private final List<LanguageRunner> runners;
    private final AtomicLong queueSequence = new AtomicLong();
    private ThreadPoolExecutor executionExecutor;

    @Value("${sandbox.execution.workers:2}")
    private int executionWorkers;

    public CompileResponse compile(CompileRequest request) {
        LanguageRunner runner = resolveRunner(request.getLanguage());
        RunnerCompileResult result = runner.compile(request.getSourceCode());
        return CompileResponse.builder()
                .success(result.isSuccess())
                .compileErrors(result.getErrors())
                .message(result.isSuccess() ? "Compilation successful" : "Compilation failed")
                .build();
    }

    public ExecuteResponse execute(ExecuteRequest request) {
        PrioritizedExecutionTask task = new PrioritizedExecutionTask(
                request.getExecutionPriority() == null ? ExecutionPriority.REGISTERED_INTERVIEW : request.getExecutionPriority(),
                queueSequence.incrementAndGet(),
                () -> executeNow(request)
        );
        executor().execute(task);
        try {
            return task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ExecuteResponse.builder()
                    .success(false)
                    .message("Execution was interrupted")
                    .stderr("Execution was interrupted")
                    .build();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Sandbox execution failed", cause);
        }
    }

    private ExecuteResponse executeNow(ExecuteRequest request) {
        LanguageRunner runner = resolveRunner(request.getLanguage());
        long timeoutMs = request.getTimeoutMs() > 0
                ? request.getTimeoutMs()
                : runner.defaultTimeoutMs();
        long memoryMb = request.getMemoryLimitMb() > 0
                ? Math.min(request.getMemoryLimitMb(), runner.maxMemoryMb())
                : runner.defaultMemoryMb();

        RunnerExecutionResult result = runner.execute(request.getSourceCode(), timeoutMs, memoryMb);
        return ExecuteResponse.builder()
                .success(result.isSuccess())
                .stdout(result.getStdout())
                .stderr(result.getStderr())
                .compileErrors(result.getCompileErrors())
                .exitCode(result.getExitCode())
                .executionTimeMs(result.getExecutionTimeMs())
                .message(buildMessage(result))
                .build();
    }

    private synchronized ThreadPoolExecutor executor() {
        if (executionExecutor == null) {
            int workers = Math.max(1, executionWorkers);
            executionExecutor = new ThreadPoolExecutor(
                    workers,
                    workers,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new PriorityBlockingQueue<>()
            );
            log.info("Sandbox execution priority queue started with {} worker(s)", workers);
        }
        return executionExecutor;
    }

    @PreDestroy
    void shutdown() {
        if (executionExecutor != null) {
            executionExecutor.shutdownNow();
        }
    }

    private LanguageRunner resolveRunner(ExecutionLanguage language) {
        ExecutionLanguage effectiveLanguage = language == null ? ExecutionLanguage.JAVA : language;
        return runners.stream()
                .filter(runner -> runner.supports(effectiveLanguage))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No runner configured for language " + effectiveLanguage));
    }

    private String buildMessage(RunnerExecutionResult result) {
        if (!result.getCompileErrors().isEmpty()) {
            return "Compilation failed";
        }
        if (!result.isSuccess()) {
            return result.getErrorMessage();
        }
        if (result.getExitCode() != 0) {
            return "Execution completed with exit code: " + result.getExitCode();
        }
        return "Execution successful";
    }

    private static final class PrioritizedExecutionTask extends FutureTask<ExecuteResponse>
            implements Comparable<PrioritizedExecutionTask> {
        private final ExecutionPriority priority;
        private final long sequence;

        private PrioritizedExecutionTask(ExecutionPriority priority,
                                         long sequence,
                                         Callable<ExecuteResponse> callable) {
            super(callable);
            this.priority = priority;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(PrioritizedExecutionTask other) {
            int priorityComparison = Integer.compare(priorityRank(this.priority), priorityRank(other.priority));
            if (priorityComparison != 0) {
                return priorityComparison;
            }
            return Long.compare(this.sequence, other.sequence);
        }

        private static int priorityRank(ExecutionPriority priority) {
            return priority == ExecutionPriority.PREPARATION ? 10 : 0;
        }
    }
}
