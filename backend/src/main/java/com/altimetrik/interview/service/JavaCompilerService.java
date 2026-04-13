package com.altimetrik.interview.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for compiling and executing Java code in a sandboxed environment.
 * Handles temporary file management, process execution, and resource constraints.
 */
@Service
@Slf4j
public class JavaCompilerService {

    private static final String TEMP_DIR_PREFIX = "java-compiler-";
    private static final long MAX_MEMORY_MB = 1024;
    private static final long DEFAULT_TIMEOUT_SECONDS = 5;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;\\s*$", Pattern.MULTILINE);
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile("public\\s+(?:\\w+\\s+)*class\\s+(\\w+)");

    private record EntryPoint(String runClassName, Path sourceFile, Path classFile) {}

    /**
     * Compiles Java source code to bytecode.
     * Returns compilation errors if compilation fails.
     */
    public CompileResult compile(String sourceCode) {
        Path workDir = null;
        try {
            // Create temporary directory for this compilation
            workDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            log.debug("Created temp directory: {}", workDir);

            // Write source code to file
            EntryPoint entryPoint = writeSourceFile(workDir, sourceCode);

            // Compile using javac
            return compileWithJavac(workDir, entryPoint.sourceFile());
        } catch (Exception e) {
            log.error("Compilation error", e);
            return CompileResult.failure(List.of(e.getMessage()));
        }
    }

    /**
     * Executes compiled Java code and captures output.
     */
    public ExecutionResult execute(String sourceCode, long timeoutMs, long memoryLimitMb) {
        Path workDir = null;
        try {
            // Create temporary directory
            workDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            log.debug("Created temp directory: {}", workDir);

            // Write and compile source code
            EntryPoint entryPoint = writeSourceFile(workDir, sourceCode);
            CompileResult compileResult = compileWithJavac(workDir, entryPoint.sourceFile());

            if (!compileResult.isSuccess()) {
                return ExecutionResult.compilationFailed(compileResult.getErrors());
            }

            // Execute the compiled code
            return executeCompiledClass(workDir, timeoutMs, memoryLimitMb, entryPoint.runClassName(), entryPoint.classFile());
        } catch (Exception e) {
            log.error("Execution error", e);
            return ExecutionResult.error(e.getMessage());
        } finally {
            // Clean up temporary directory
            if (workDir != null) {
                try {
                    FileUtils.deleteDirectory(workDir.toFile());
                    log.debug("Cleaned up temp directory: {}", workDir);
                } catch (IOException e) {
                    log.warn("Failed to cleanup temp directory: {}", workDir, e);
                }
            }
        }
    }

    /**
     * Writes Java source code to a file in the work directory.
     * Extracts the class name from the source code.
     */
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

    /**
     * Extracts the public class name from Java source code.
     */
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

    /**
     * Compiles Java source using javac command.
     */
    private CompileResult compileWithJavac(Path workDir, Path sourceFile) throws IOException {
        // Use libs for dev + Docker
        Path libDir = workDir.resolve("libs");
        Files.createDirectories(libDir);
        
        // Copy libs for reliable classpath
        String libsEnv = System.getenv("SANDBOX_LIBS");
        Path libsPath = (libsEnv != null) ? Path.of(libsEnv) : Path.of("/app/libs");
        if (Files.exists(libsPath)) {
            try (var libs = Files.walk(libsPath)) {
                libs.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .forEach(lib -> {
                        try {
                            Path target = libDir.resolve(libsPath.relativize(lib).toString());
                            Files.createDirectories(target.getParent());
                            Files.copy(lib, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            log.warn("Failed to copy lib {}: {}", lib, e.getMessage());
                        }
                    });
            }
            String cp = workDir.toString() + File.pathSeparator + libDir.toString();
            log.debug("Classpath: {}", cp);
        }
        
        String cp = workDir.toString() + File.pathSeparator + libDir.toString();
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
                return CompileResult.success();
            } else {
                List<String> errors = parseCompilationErrors(output);
                log.warn("Compilation failed with errors: {}", errors);
                return CompileResult.failure(errors);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Compilation interrupted", e);
            return CompileResult.failure(List.of("Compilation timeout"));
        }
    }

    /**
     * Executes the compiled Java class with resource constraints.
     */
    private ExecutionResult executeCompiledClass(Path workDir, long timeoutMs, long memoryLimitMb, String runClassName, Path classFile)
            throws IOException {
        if (runClassName == null || runClassName.isBlank()) {
            return ExecutionResult.error("Main class name is missing");
        }

        if (classFile == null || !classFile.toFile().exists()) {
            return ExecutionResult.error("Compiled class file not found");
        }

        List<String> command = buildJavaCommand(workDir, memoryLimitMb, runClassName);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workDir.toFile());
        processBuilder.redirectErrorStream(false);

        long startTime = System.currentTimeMillis();
        try {
            Process process = processBuilder.start();
            
            // Capture output in separate threads to avoid deadlock
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<String> stdoutFuture = executor.submit(() -> readStream(process.getInputStream()));
            Future<String> stderrFuture = executor.submit(() -> readStream(process.getErrorStream()));

            boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long executionTime = System.currentTimeMillis() - startTime;

            if (!completed) {
                process.destroyForcibly();
                executor.shutdownNow();
                log.warn("Execution timeout after {}ms", timeoutMs);
                return ExecutionResult.timeout(timeoutMs);
            }

            String stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(1, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            executor.shutdown();

            log.debug("Execution completed with exit code: {}", exitCode);
            return ExecutionResult.success(stdout, stderr, exitCode, executionTime);
        } catch (ExecutionException e) {
            log.error("Task execution failed", e);
            return ExecutionResult.error("Failed during output reading: " + e.getCause().getMessage());
        } catch (TimeoutException e) {
            log.error("Output reading timeout", e);
            return ExecutionResult.error("Failed to read execution output");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Execution interrupted", e);
            return ExecutionResult.error("Execution interrupted");
        }
    }

    /**
     * Builds the Java command with JVM options for resource constraints.
     */
    private List<String> buildJavaCommand(Path workDir, long memoryLimitMb, String className) {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-Xmx" + memoryLimitMb + "m");
        command.add("-Xms64m");
        
        // Copy libs to workdir for runtime too
        Path libDir = workDir.resolve("libs");
        try {
            Files.createDirectories(libDir);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        String libsEnv = System.getenv("SANDBOX_LIBS");
        Path libsPath = (libsEnv != null) ? Path.of(libsEnv) : Path.of("/app/libs");
        if (Files.exists(libsPath)) {
            try {
                Files.walk(libsPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .forEach(lib -> {
                        try {
                            Path target = libDir.resolve(libsPath.relativize(lib).toString());
                            Files.createDirectories(target.getParent());
                            Files.copy(lib, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            log.warn("Failed to copy lib {}: {}", lib, e.getMessage());
                        }
                    });
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        String cp = workDir.toString() + File.pathSeparator + libDir.toString();
        command.add("-cp");
        command.add(cp);
        command.add(className);
        return command;
    }

    /**
     * Reads output from a process stream.
     */
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

    /**
     * Reads from an input stream (for non-blocking output capture).
     */
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

    /**
     * Parses javac error output into a list of error messages.
     */
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

    /**
     * Data class for compilation results.
     */
    public static class CompileResult {
        private final boolean success;
        private final List<String> errors;

        private CompileResult(boolean success, List<String> errors) {
            this.success = success;
            this.errors = errors != null ? errors : Collections.emptyList();
        }

        public static CompileResult success() {
            return new CompileResult(true, Collections.emptyList());
        }

        public static CompileResult failure(List<String> errors) {
            return new CompileResult(false, errors);
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * Data class for execution results.
     */
    public static class ExecutionResult {
        private final boolean success;
        private final String stdout;
        private final String stderr;
        private final List<String> compileErrors;
        private final int exitCode;
        private final long executionTimeMs;
        private final String errorMessage;

        private ExecutionResult(boolean success, String stdout, String stderr, 
                               List<String> compileErrors, int exitCode, 
                               long executionTimeMs, String errorMessage) {
            this.success = success;
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
            this.compileErrors = compileErrors != null ? compileErrors : Collections.emptyList();
            this.exitCode = exitCode;
            this.executionTimeMs = executionTimeMs;
            this.errorMessage = errorMessage != null ? errorMessage : "";
        }

        public static ExecutionResult success(String stdout, String stderr, 
                                             int exitCode, long executionTimeMs) {
            return new ExecutionResult(true, stdout, stderr, Collections.emptyList(), 
                                      exitCode, executionTimeMs, "");
        }

        public static ExecutionResult compilationFailed(List<String> errors) {
            return new ExecutionResult(false, "", "", errors, -1, 0, 
                                      "Compilation failed");
        }

        public static ExecutionResult timeout(long timeoutMs) {
            return new ExecutionResult(false, "", "", Collections.emptyList(), -1, 
                                      timeoutMs, "Execution timeout");
        }

        public static ExecutionResult error(String message) {
            return new ExecutionResult(false, "", "", Collections.emptyList(), -1, 0, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public List<String> getCompileErrors() {
            return compileErrors;
        }

        public int getExitCode() {
            return exitCode;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
