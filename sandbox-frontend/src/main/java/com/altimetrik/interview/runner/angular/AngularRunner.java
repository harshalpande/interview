package com.altimetrik.interview.runner.angular;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.altimetrik.interview.dto.EditableCodeFileDto;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.runner.PersistentFrontendRunner;
import com.altimetrik.interview.runner.model.FrontendBuildResult;
import com.altimetrik.interview.service.PreviewStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AngularRunner implements PersistentFrontendRunner {

    private static final long DEFAULT_TIMEOUT_MS = Duration.ofSeconds(45).toMillis();
    private static final int MAX_EDITABLE_FILES = 20;
    private static final int MAX_TOTAL_BYTES = 512 * 1024;
    private static final int MAX_FILE_BYTES = 128 * 1024;
    private static final Set<String> ALLOWED_FILE_SUFFIXES = Set.of(".ts", ".html", ".css");
    private static final String TEMPLATE_ROOT_ENV = "ANGULAR_TEMPLATE_ROOT";
    private static final String DEFAULT_TEMPLATE_ROOT = "/opt/angular-template";
    private static final String FALLBACK_TEMPLATE_ROOT = "sandbox-frontend/template/angular-workspace";
    private static final List<EditableCodeFileDto> DEFAULT_FILES = List.of(
            editableFile("src/app/app.component.ts", "app.component.ts", """
                    import { Component } from '@angular/core';
                    import { CommonModule } from '@angular/common';

                    @Component({
                      selector: 'app-root',
                      standalone: true,
                      imports: [CommonModule],
                      templateUrl: './app.component.html',
                      styleUrl: './app.component.css'
                    })
                    export class AppComponent {
                      title = 'Angular interview sandbox';
                    }
                    """, 0),
            editableFile("src/app/app.component.html", "app.component.html", """
                    <main class="app-shell">
                      <h1>{{ title }}</h1>
                      <p>Start building your Angular solution here.</p>
                    </main>
                    """, 1),
            editableFile("src/app/app.component.css", "app.component.css", """
                    .app-shell {
                      display: grid;
                      gap: 12px;
                      padding: 24px;
                      font-family: Arial, sans-serif;
                    }

                    h1 {
                      margin: 0;
                      color: #0f3d59;
                    }

                    p {
                      margin: 0;
                      color: #4f6474;
                    }
                    """, 2)
    );
    private final PreviewStorageService previewStorageService;

    @Override
    public boolean supports(ExecutionLanguage language) {
        return language == ExecutionLanguage.ANGULAR;
    }

    @Override
    public long defaultTimeoutMs() {
        return DEFAULT_TIMEOUT_MS;
    }

    @Override
    public FrontendBuildResult build(List<EditableCodeFileDto> files, long timeoutMs) {
        try {
            Path workspaceDir = createWorkspace(files);
            try {
                return buildInWorkspace(workspaceDir, timeoutMs);
            } finally {
                destroyWorkspace(workspaceDir);
            }
        } catch (IllegalArgumentException exception) {
            return FrontendBuildResult.compilationFailed(List.of(exception.getMessage()), "", "", -1, 0);
        } catch (Exception exception) {
            log.error("Angular build error", exception);
            return FrontendBuildResult.error(exception.getMessage());
        }
    }

    @Override
    public Path createWorkspace(List<EditableCodeFileDto> files) throws IOException {
        validateEditableFiles(files);
        Path workspaceDir = Files.createTempDirectory("angular-workspace-");
        Path templateRoot = resolveTemplateRoot();
        log.info("Angular workspace initializing with templateRoot={} workspaceDir={} editableFileCount={}",
                templateRoot, workspaceDir, files == null ? DEFAULT_FILES.size() : files.size());
        copyTemplateWorkspace(templateRoot, workspaceDir);
        patchEditableFiles(workspaceDir, files == null || files.isEmpty() ? DEFAULT_FILES : files);
        return workspaceDir;
    }

    @Override
    public void patchWorkspaceFiles(Path workspaceDir, List<EditableCodeFileDto> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return;
        }
        validateEditableFiles(files);
        patchEditableFiles(workspaceDir, files);
    }

    @Override
    public FrontendBuildResult buildInWorkspace(Path workspaceDir, long timeoutMs) {
        try {
            log.info("Angular warm build starting in persistent workspace={}", workspaceDir);
            return runBuild(workspaceDir, timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS);
        } catch (Exception exception) {
            log.error("Angular build error in persistent workspace {}", workspaceDir, exception);
            return FrontendBuildResult.error(exception.getMessage());
        }
    }

    @Override
    public void destroyWorkspace(Path workspaceDir) {
        cleanupWorkspace(workspaceDir);
    }

    @Override
    public Process startWatchProcess(Path workspaceDir) throws IOException {
        List<String> command = List.of(
                "npm",
                "run",
                "build",
                "--",
                "--watch",
                "--configuration",
                "development",
                "--base-href",
                "./"
        );
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workspaceDir.toFile());
        processBuilder.redirectErrorStream(false);
        return processBuilder.start();
    }

    @Override
    public Path resolveWorkspacePreviewRoot(Path workspaceDir) {
        return workspaceDir.resolve("dist").resolve("interview-angular-sandbox").resolve("browser");
    }

    private void validateEditableFiles(List<EditableCodeFileDto> files) {
        List<EditableCodeFileDto> effectiveFiles = files == null || files.isEmpty() ? DEFAULT_FILES : files;
        if (effectiveFiles.size() > MAX_EDITABLE_FILES) {
            throw new IllegalArgumentException("Too many editable Angular files were provided");
        }
        int totalBytes = 0;
        Set<String> seenPaths = new java.util.LinkedHashSet<>();
        for (EditableCodeFileDto file : effectiveFiles) {
            String path = file.getPath();
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("Every editable Angular file must include a path");
            }
            String normalized = path.replace('\\', '/');
            if (!seenPaths.add(normalized)) {
                throw new IllegalArgumentException("Duplicate Angular workspace path: " + path);
            }
            if (!normalized.startsWith("src/app/")) {
                throw new IllegalArgumentException("Only files under src/app are editable in the Angular sandbox");
            }
            if (normalized.contains("..") || normalized.startsWith("/")) {
                throw new IllegalArgumentException("Invalid Angular workspace path: " + path);
            }
            boolean suffixAllowed = ALLOWED_FILE_SUFFIXES.stream().anyMatch(normalized::endsWith);
            if (!suffixAllowed) {
                throw new IllegalArgumentException("Only .ts, .html, and .css files are supported in the Angular sandbox");
            }
            int fileBytes = (file.getContent() == null ? 0 : file.getContent().getBytes(StandardCharsets.UTF_8).length);
            if (fileBytes > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Angular file is too large: " + path);
            }
            totalBytes += fileBytes;
        }
        if (totalBytes > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("Angular workspace content is too large");
        }
    }

    private Path resolveTemplateRoot() {
        String configured = System.getenv(TEMPLATE_ROOT_ENV);
        if (configured != null && !configured.isBlank()) {
            Path configuredPath = Path.of(configured);
            if (Files.exists(configuredPath)) {
                return configuredPath;
            }
        }

        Path dockerTemplate = Path.of(DEFAULT_TEMPLATE_ROOT);
        if (Files.exists(dockerTemplate)) {
            return dockerTemplate;
        }

        Path localTemplate = Path.of(FALLBACK_TEMPLATE_ROOT);
        if (Files.exists(localTemplate)) {
            return localTemplate;
        }

        throw new IllegalStateException("Angular template workspace is not available");
    }

    private void copyTemplateWorkspace(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                if (relative.toString().isBlank()) {
                    continue;
                }
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                    continue;
                }
                if (relative.startsWith("dist") || relative.startsWith(".angular")) {
                    continue;
                }
                if (relative.startsWith("node_modules")) {
                    continue;
                }
                Files.createDirectories(destination.getParent());
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Path templateNodeModules = source.resolve("node_modules");
        if (Files.exists(templateNodeModules)) {
            try {
                Files.createSymbolicLink(target.resolve("node_modules"), templateNodeModules);
            } catch (UnsupportedOperationException | IOException exception) {
                copyDirectory(templateNodeModules, target.resolve("node_modules"));
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isSymbolicLink(path)) {
                    Files.createDirectories(Objects.requireNonNull(destination.getParent()));
                    try {
                        Files.createSymbolicLink(destination, Files.readSymbolicLink(path));
                    } catch (UnsupportedOperationException | IOException exception) {
                        Path resolvedTarget = path.toRealPath();
                        if (Files.isDirectory(resolvedTarget)) {
                            copyDirectory(resolvedTarget, destination);
                        } else {
                            Files.copy(resolvedTarget, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void patchEditableFiles(Path workspaceDir, List<EditableCodeFileDto> files) throws IOException {
        List<EditableCodeFileDto> effectiveFiles = files == null || files.isEmpty() ? DEFAULT_FILES : files;
        for (EditableCodeFileDto file : effectiveFiles) {
            Path destination = workspaceDir.resolve(file.getPath().replace('/', java.io.File.separatorChar));
            Files.createDirectories(Objects.requireNonNull(destination.getParent()));
            Files.writeString(destination, file.getContent() == null ? "" : file.getContent(), StandardCharsets.UTF_8);
        }
    }

    private FrontendBuildResult runBuild(Path workspaceDir, long timeoutMs) throws IOException {
        List<String> command = List.of("npm", "run", "build", "--", "--base-href", "./");
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workspaceDir.toFile());
        processBuilder.redirectErrorStream(false);

        long startTime = System.currentTimeMillis();
        try {
            Process process = processBuilder.start();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<String> stdoutFuture = executor.submit(() -> readStream(process.getInputStream()));
            Future<String> stderrFuture = executor.submit(() -> readStream(process.getErrorStream()));

            boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long executionTimeMs = System.currentTimeMillis() - startTime;

            if (!completed) {
                process.destroyForcibly();
                executor.shutdownNow();
                return FrontendBuildResult.timeout(timeoutMs);
            }

            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            executor.shutdown();

            if (exitCode != 0) {
                return FrontendBuildResult.compilationFailed(parseBuildErrors(stdout, stderr), stdout, stderr, exitCode, executionTimeMs);
            }
            Path previewRoot = resolvePreviewRoot(workspaceDir);
            String previewId = previewStorageService.storePreview(previewRoot);
            log.info("Angular build completed successfully workspaceDir={} previewRoot={} previewId={}",
                    workspaceDir, previewRoot, previewId);
            return FrontendBuildResult.success(stdout, stderr, exitCode, executionTimeMs, "/workspace/preview/" + previewId + "/");
        } catch (ExecutionException exception) {
            return FrontendBuildResult.error("Failed during Angular build output reading: " + exception.getCause().getMessage());
        } catch (TimeoutException exception) {
            return FrontendBuildResult.error("Failed to read Angular build output");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return FrontendBuildResult.error("Angular build interrupted");
        }
    }

    private Path resolvePreviewRoot(Path workspaceDir) throws IOException {
        Path conventionalRoot = workspaceDir.resolve("dist").resolve("interview-angular-sandbox").resolve("browser");
        if (Files.exists(conventionalRoot.resolve("index.html"))) {
            return conventionalRoot;
        }

        try (Stream<Path> paths = Files.walk(workspaceDir.resolve("dist"))) {
            return paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals("index.html"))
                    .map(Path::getParent)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Angular preview output was not found after build"));
        }
    }

    private List<String> parseBuildErrors(String stdout, String stderr) {
        List<String> errors = new ArrayList<>();
        Stream.of(stdout, stderr)
                .filter(Objects::nonNull)
                .flatMap(content -> content.lines())
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> {
                    String normalized = line.toLowerCase(Locale.ROOT);
                    return normalized.contains("error") || normalized.contains("ng") || normalized.contains("ts");
                })
                .forEach(errors::add);
        if (errors.isEmpty()) {
            if (stderr != null && !stderr.isBlank()) {
                return stderr.lines().filter(line -> !line.isBlank()).toList();
            }
            if (stdout != null && !stdout.isBlank()) {
                return stdout.lines().filter(line -> !line.isBlank()).toList();
            }
            return List.of("Angular build failed");
        }
        return errors.stream().distinct().toList();
    }

    private String readStream(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            return output.toString();
        }
    }

    private void cleanupWorkspace(Path workspaceDir) {
        if (workspaceDir == null) {
            return;
        }
        try {
            Files.walkFileTree(workspaceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            log.warn("Failed to clean Angular workspace {}", workspaceDir, exception);
        }
    }

    private static EditableCodeFileDto editableFile(String path, String displayName, String content, int sortOrder) {
        return EditableCodeFileDto.builder()
                .path(path)
                .displayName(displayName)
                .content(content)
                .editable(true)
                .sortOrder(sortOrder)
                .build();
    }
}
