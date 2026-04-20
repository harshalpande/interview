package com.altimetrik.interview.runner.python;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.runner.LanguageRunner;
import com.altimetrik.interview.runner.model.RunnerCompileResult;
import com.altimetrik.interview.runner.model.RunnerExecutionResult;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PythonRunner implements LanguageRunner {

    private static final String TEMP_DIR_PREFIX = "python-sandbox-";
    private static final String SOURCE_FILE_NAME = "main.py";
    private static final String BOOTSTRAP_FILE_NAME = "__sandbox_bootstrap__.py";
    private static final long DEFAULT_MEMORY_MB = 256;
    private static final long MAX_MEMORY_MB = 512;
    private static final long DEFAULT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern FROM_IMPORT_PATTERN = Pattern.compile("^\\s*from\\s+([\\w.]+)\\s+import\\s+.+$", Pattern.MULTILINE);
    private static final Set<String> ALLOWED_MODULES = Set.of(
            "array",
            "bisect",
            "collections",
            "dataclasses",
            "datetime",
            "decimal",
            "enum",
            "fractions",
            "functools",
            "heapq",
            "itertools",
            "json",
            "math",
            "random",
            "re",
            "statistics",
            "string",
            "time",
            "typing"
    );
    private static final List<List<String>> WINDOWS_PYTHON_COMMAND_CANDIDATES = List.of(
            List.of("py", "-3.12"),
            List.of("python"),
            List.of("py", "-3")
    );
    private static final List<List<String>> POSIX_PYTHON_COMMAND_CANDIDATES = List.of(
            List.of("python3.12"),
            List.of("python3"),
            List.of("python")
    );
    private static final String SANDBOX_BOOTSTRAP = """
            import builtins
            import pathlib
            import sys

            _allowed_roots = [pathlib.Path.cwd().resolve()]
            _original_import = builtins.__import__
            _original_open = builtins.open

            def _is_allowed_module(module_name: str) -> bool:
                root_name = module_name.split(".", 1)[0]
                return root_name in {
                    "array", "bisect", "collections", "dataclasses", "datetime", "decimal",
                    "enum", "fractions", "functools", "heapq", "itertools", "json", "math",
                    "random", "re", "statistics", "string", "time", "typing"
                }

            def _safe_import(name, globals=None, locals=None, fromlist=(), level=0):
                if level != 0:
                    raise ImportError("Relative imports are not allowed in the Python sandbox")
                if not _is_allowed_module(name):
                    raise ImportError(f"Import of '{name}' is not allowed in the Python sandbox")
                return _original_import(name, globals, locals, fromlist, level)

            def _safe_open(file, mode="r", *args, **kwargs):
                requested = pathlib.Path(file)
                if not requested.is_absolute():
                    requested = pathlib.Path.cwd() / requested
                resolved = requested.resolve()
                if not any(resolved == root or root in resolved.parents for root in _allowed_roots):
                    raise PermissionError("File access outside the sandbox workspace is not allowed")
                return _original_open(resolved, mode, *args, **kwargs)

            builtins.__import__ = _safe_import
            builtins.open = _safe_open

            _source_path = pathlib.Path.cwd() / "main.py"
            _source_code = _source_path.read_text(encoding="utf-8")
            _code = compile(_source_code, str(_source_path), "exec")
            _globals = {"__name__": "__main__", "__file__": str(_source_path)}
            exec(_code, _globals)
            """;

    @Override
    public boolean supports(ExecutionLanguage language) {
        return language == ExecutionLanguage.PYTHON;
    }

    @Override
    public long defaultTimeoutMs() {
        return DEFAULT_TIMEOUT_MS;
    }

    @Override
    public long defaultMemoryMb() {
        return DEFAULT_MEMORY_MB;
    }

    @Override
    public long maxMemoryMb() {
        return MAX_MEMORY_MB;
    }

    @Override
    public RunnerCompileResult compile(String sourceCode) {
        List<String> importErrors = validateImports(sourceCode);
        if (!importErrors.isEmpty()) {
            return RunnerCompileResult.failure(importErrors);
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            Path sourceFile = writeSourceFile(workDir, sourceCode);
            return runSyntaxCheck(workDir, sourceFile);
        } catch (Exception exception) {
            log.error("Python compilation error", exception);
            return RunnerCompileResult.failure(List.of(exception.getMessage()));
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    @Override
    public RunnerExecutionResult execute(String sourceCode, long timeoutMs, long memoryLimitMb) {
        List<String> importErrors = validateImports(sourceCode);
        if (!importErrors.isEmpty()) {
            return RunnerExecutionResult.compilationFailed(importErrors);
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            Path sourceFile = writeSourceFile(workDir, sourceCode);
            RunnerCompileResult compileResult = runSyntaxCheck(workDir, sourceFile);
            if (!compileResult.isSuccess()) {
                return RunnerExecutionResult.compilationFailed(compileResult.getErrors());
            }

            Files.writeString(workDir.resolve(BOOTSTRAP_FILE_NAME), SANDBOX_BOOTSTRAP, StandardCharsets.UTF_8);
            return runPythonProcess(workDir, sanitizeTimeoutMs(timeoutMs), sanitizeMemoryLimitMb(memoryLimitMb));
        } catch (Exception exception) {
            log.error("Python execution error", exception);
            return RunnerExecutionResult.error(exception.getMessage());
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    private Path writeSourceFile(Path workDir, String sourceCode) throws IOException {
        Path sourceFile = workDir.resolve(SOURCE_FILE_NAME);
        Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);
        return sourceFile;
    }

    private RunnerCompileResult runSyntaxCheck(Path workDir, Path sourceFile) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(resolvePythonCommand());
        command.add("-m");
        command.add("py_compile");
        command.add(sourceFile.getFileName().toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workDir.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = readProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            return RunnerCompileResult.success();
        }

        return RunnerCompileResult.failure(parsePythonErrors(output));
    }

    private RunnerExecutionResult runPythonProcess(Path workDir, long timeoutMs, long memoryLimitMb) throws IOException {
        List<String> command = new ArrayList<>(resolvePythonCommand());
        command.add(BOOTSTRAP_FILE_NAME);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workDir.toFile());
        processBuilder.environment().putIfAbsent("PYTHONNOUSERSITE", "1");
        processBuilder.environment().putIfAbsent("PYTHONDONTWRITEBYTECODE", "1");
        processBuilder.redirectErrorStream(false);

        long startTime = System.currentTimeMillis();
        try {
            Process process = processBuilder.start();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<String> stdoutFuture = executor.submit(() -> readStream(process.getInputStream()));
            Future<String> stderrFuture = executor.submit(() -> readStream(process.getErrorStream()));

            boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long executionTime = System.currentTimeMillis() - startTime;

            if (!completed) {
                process.destroyForcibly();
                executor.shutdownNow();
                return RunnerExecutionResult.timeout(timeoutMs);
            }

            String stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(1, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            executor.shutdown();

            if (memoryMbExceeded(stderr, memoryLimitMb)) {
                return RunnerExecutionResult.error("Python sandbox memory limit exceeded");
            }

            return RunnerExecutionResult.success(stdout, stderr, exitCode, executionTime);
        } catch (ExecutionException exception) {
            return RunnerExecutionResult.error("Failed during Python output reading: " + exception.getCause().getMessage());
        } catch (TimeoutException exception) {
            return RunnerExecutionResult.error("Failed to read Python execution output");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RunnerExecutionResult.error("Python execution interrupted");
        }
    }

    private boolean memoryMbExceeded(String stderr, long memoryLimitMb) {
        if (memoryLimitMb <= 0 || stderr == null) {
            return false;
        }
        String normalized = stderr.toLowerCase(Locale.ROOT);
        return normalized.contains("memoryerror") || normalized.contains("memory error");
    }

    private List<String> resolvePythonCommand() {
        String configured = System.getenv("PYTHON_EXECUTABLE");
        if (configured != null && !configured.isBlank()) {
            return List.of(configured);
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return flattenResolvedCommand(windows ? WINDOWS_PYTHON_COMMAND_CANDIDATES : POSIX_PYTHON_COMMAND_CANDIDATES);
    }

    private List<String> flattenResolvedCommand(List<List<String>> candidates) {
        for (List<String> candidate : candidates) {
            try {
                Process process = new ProcessBuilder(candidate)
                        .redirectErrorStream(true)
                        .start();
                boolean completed = process.waitFor(2, TimeUnit.SECONDS);
                if (completed && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException("Python 3.12 runtime is not available for the sandbox");
    }

    private List<String> validateImports(String sourceCode) {
        Set<String> modules = new LinkedHashSet<>();
        Matcher importMatcher = IMPORT_PATTERN.matcher(sourceCode);
        while (importMatcher.find()) {
            String clause = importMatcher.group(1);
            for (String part : clause.split(",")) {
                String normalized = part.trim();
                if (normalized.isEmpty()) {
                    continue;
                }
                String module = normalized.split("\\s+as\\s+", 2)[0].trim();
                modules.add(rootModule(module));
            }
        }

        Matcher fromImportMatcher = FROM_IMPORT_PATTERN.matcher(sourceCode);
        while (fromImportMatcher.find()) {
            modules.add(rootModule(fromImportMatcher.group(1).trim()));
        }

        List<String> errors = new ArrayList<>();
        for (String module : modules) {
            if (!ALLOWED_MODULES.contains(module)) {
                errors.add("Import of module '" + module + "' is not allowed in the Python sandbox");
            }
        }
        return errors;
    }

    private String rootModule(String module) {
        return module.split("\\.", 2)[0];
    }

    private long sanitizeTimeoutMs(long timeoutMs) {
        return timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    private long sanitizeMemoryLimitMb(long memoryLimitMb) {
        if (memoryLimitMb <= 0) {
            return DEFAULT_MEMORY_MB;
        }
        return Math.min(memoryLimitMb, MAX_MEMORY_MB);
    }

    private void cleanupWorkDir(Path workDir) {
        if (workDir == null) {
            return;
        }
        try {
            FileUtils.deleteDirectory(workDir.toFile());
        } catch (IOException exception) {
            log.warn("Failed to cleanup Python temp directory {}", workDir, exception);
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            return output.toString();
        }
    }

    private String readStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            return output.toString();
        } catch (IOException exception) {
            log.error("Error reading Python process stream", exception);
            return "";
        }
    }

    private List<String> parsePythonErrors(String output) {
        if (output == null || output.isBlank()) {
            return List.of("Python compilation failed");
        }
        return output.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
