package com.altimetrik.interview.runner.java;

import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.runner.LanguageRunner;
import com.altimetrik.interview.runner.model.RunnerCompileResult;
import com.altimetrik.interview.runner.model.RunnerExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class JavaRunner implements LanguageRunner {

    private static final String TEMP_DIR_PREFIX = "java-compiler-";
    private static final long DEFAULT_MEMORY_MB = 512;
    private static final long MAX_MEMORY_MB = 1024;
    private static final long DEFAULT_TIMEOUT_SECONDS = 5;
    private static final long DEFAULT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(DEFAULT_TIMEOUT_SECONDS);
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;\\s*$", Pattern.MULTILINE);
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile("public\\s+(?:\\w+\\s+)*class\\s+(\\w+)");
    private static final Path DOCKER_LIBS_PATH = Path.of("/app/libs");
    private static final Path LOCAL_LIBS_PATH = Path.of("libs");
    private static final Path LEGACY_LOCAL_LIBS_PATH = Path.of("../backend/libs");

    private record EntryPoint(String runClassName, Path sourceFile, Path classFile) {}

    @Override
    public boolean supports(ExecutionLanguage language) {
        return language == ExecutionLanguage.JAVA;
    }

    @Override
    public RunnerCompileResult compile(String sourceCode) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            log.debug("Created temp directory: {}", workDir);

            EntryPoint entryPoint = writeSourceFile(workDir, sourceCode);
            return compileWithJavac(workDir, entryPoint.sourceFile());
        } catch (Exception e) {
            log.error("Compilation error", e);
            return RunnerCompileResult.failure(List.of(e.getMessage()));
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    @Override
    public RunnerExecutionResult execute(String sourceCode, long timeoutMs, long memoryLimitMb) {
        long effectiveTimeoutMs = sanitizeTimeoutMs(timeoutMs);
        long effectiveMemoryLimitMb = sanitizeMemoryLimitMb(memoryLimitMb);
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            log.debug("Created temp directory: {}", workDir);

            EntryPoint entryPoint = writeSourceFile(workDir, sourceCode);
            RunnerCompileResult compileResult = compileWithJavac(workDir, entryPoint.sourceFile());
            if (!compileResult.isSuccess()) {
                return RunnerExecutionResult.compilationFailed(compileResult.getErrors());
            }

            return executeCompiledClass(workDir, effectiveTimeoutMs, effectiveMemoryLimitMb, entryPoint.runClassName(), entryPoint.classFile());
        } catch (Exception e) {
            log.error("Execution error", e);
            return RunnerExecutionResult.error(e.getMessage());
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    public static long defaultTimeoutMs() {
        return DEFAULT_TIMEOUT_MS;
    }

    public static long defaultMemoryMb() {
        return DEFAULT_MEMORY_MB;
    }

    public static long maxMemoryMb() {
        return MAX_MEMORY_MB;
    }

    private void cleanupWorkDir(Path workDir) {
        if (workDir != null) {
            try {
                FileUtils.deleteDirectory(workDir.toFile());
                log.debug("Cleaned up temp directory: {}", workDir);
            } catch (IOException e) {
                log.warn("Failed to cleanup temp directory: {}", workDir, e);
            }
        }
    }

    private EntryPoint writeSourceFile(Path workDir, String sourceCode) throws IOException {
        String className = extractPublicClassName(sourceCode);
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Could not find public class declaration in source code");
        }

        String packageName = extractPackageName(sourceCode);
        Path packageDir = packageName == null ? workDir : workDir.resolve(packageName.replace('.', File.separatorChar));
        Files.createDirectories(packageDir);

        Path sourceFile = packageDir.resolve(className + ".java");
        Files.write(sourceFile, sourceCode.getBytes(StandardCharsets.UTF_8));
        log.debug("Wrote source file: {}", sourceFile);

        String runClassName = packageName == null ? className : packageName + "." + className;
        Path classFile = packageDir.resolve(className + ".class");
        return new EntryPoint(runClassName, sourceFile, classFile);
    }

    private String extractPublicClassName(String sourceCode) {
        Matcher matcher = PUBLIC_CLASS_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractPackageName(String sourceCode) {
        Matcher matcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private RunnerCompileResult compileWithJavac(Path workDir, Path sourceFile) throws IOException {
        String cp = buildClasspath(workDir);
        log.debug("Classpath: {}", cp);

        List<String> javacArgs = new ArrayList<>();
        javacArgs.add("javac");
        javacArgs.add("-cp");
        javacArgs.add(cp);
        javacArgs.add("-encoding");
        javacArgs.add("UTF-8");
        javacArgs.add(workDir.relativize(sourceFile).toString());

        ProcessBuilder processBuilder = new ProcessBuilder(javacArgs);
        processBuilder.directory(workDir.toFile());
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            String output = readProcessOutput(process);

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.debug("Compilation successful");
                return RunnerCompileResult.success();
            }

            List<String> errors = parseCompilationErrors(output);
            log.warn("Compilation failed with errors: {}", errors);
            return RunnerCompileResult.failure(errors);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Compilation interrupted", e);
            return RunnerCompileResult.failure(List.of("Compilation timeout"));
        }
    }

    private RunnerExecutionResult executeCompiledClass(Path workDir, long timeoutMs, long memoryLimitMb, String runClassName, Path classFile)
            throws IOException {
        if (runClassName == null || runClassName.isBlank()) {
            return RunnerExecutionResult.error("Main class name is missing");
        }

        if (classFile == null || !classFile.toFile().exists()) {
            return RunnerExecutionResult.error("Compiled class file not found");
        }

        List<String> command = buildJavaCommand(workDir, memoryLimitMb, runClassName);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workDir.toFile());
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
                log.warn("Execution timeout after {}ms", timeoutMs);
                return RunnerExecutionResult.timeout(timeoutMs);
            }

            String stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(1, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            executor.shutdown();

            log.debug("Execution completed with exit code: {}", exitCode);
            return RunnerExecutionResult.success(stdout, stderr, exitCode, executionTime);
        } catch (ExecutionException e) {
            log.error("Task execution failed", e);
            return RunnerExecutionResult.error("Failed during output reading: " + e.getCause().getMessage());
        } catch (TimeoutException e) {
            log.error("Output reading timeout", e);
            return RunnerExecutionResult.error("Failed to read execution output");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Execution interrupted", e);
            return RunnerExecutionResult.error("Execution interrupted");
        }
    }

    private List<String> buildJavaCommand(Path workDir, long memoryLimitMb, String className) {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-Xmx" + memoryLimitMb + "m");
        command.add("-Xms64m");
        command.add("-cp");
        command.add(buildClasspath(workDir));
        command.add(className);
        return command;
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

    private String buildClasspath(Path workDir) {
        Path libsPath = resolveSandboxLibrariesPath();
        if (libsPath == null) {
            return workDir.toString();
        }
        return workDir + File.pathSeparator + libsPath.resolve("*");
    }

    private Path resolveSandboxLibrariesPath() {
        String libsEnv = System.getenv("SANDBOX_LIBS");
        if (libsEnv != null && !libsEnv.isBlank()) {
            Path envPath = Path.of(libsEnv);
            if (Files.exists(envPath)) {
                return envPath;
            }
        }
        if (Files.exists(DOCKER_LIBS_PATH)) {
            return DOCKER_LIBS_PATH;
        }
        if (Files.exists(LOCAL_LIBS_PATH)) {
            return LOCAL_LIBS_PATH;
        }
        if (Files.exists(LEGACY_LOCAL_LIBS_PATH)) {
            return LEGACY_LOCAL_LIBS_PATH;
        }
        log.warn("Sandbox libraries path not found; external assertion libraries will be unavailable");
        return null;
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }

    private String readStream(InputStream inputStream) {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        } catch (IOException e) {
            log.error("Error reading stream", e);
        }
        return output.toString();
    }

    private List<String> parseCompilationErrors(String output) {
        List<String> errors = new ArrayList<>();
        if (output != null && !output.isEmpty()) {
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.contains("error:") || line.contains("Error")) {
                    errors.add(line.trim());
                }
            }
        }
        return errors.isEmpty() ? List.of(output) : errors;
    }
}
