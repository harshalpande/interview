package com.altimetrik.interview.runner.react;

import com.altimetrik.interview.dto.EditableCodeFileDto;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.runner.PersistentFrontendRunner;
import com.altimetrik.interview.runner.model.FrontendBuildResult;
import com.altimetrik.interview.service.PreviewStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
import java.util.Comparator;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class ReactRunner implements PersistentFrontendRunner {

    private static final long DEFAULT_TIMEOUT_MS = Duration.ofSeconds(45).toMillis();
    private static final int MAX_EDITABLE_FILES = 20;
    private static final int MAX_TOTAL_BYTES = 512 * 1024;
    private static final int MAX_FILE_BYTES = 128 * 1024;
    private static final Set<String> ALLOWED_FILE_SUFFIXES = Set.of(".tsx", ".ts", ".css");
    private static final String TEMPLATE_ROOT_ENV = "REACT_TEMPLATE_ROOT";
    private static final String DEFAULT_TEMPLATE_ROOT = "/opt/react-template";
    private static final String FALLBACK_TEMPLATE_ROOT = "sandbox-frontend/template/react-workspace";
    private static final List<EditableCodeFileDto> DEFAULT_FILES = List.of(
            editableFile("src/App.tsx", "App.tsx", """
                    import './App.css';

                    export default function App() {
                      return (
                        <main className="app-shell">
                          <h1>React interview sandbox</h1>
                          <p>Start building your React solution here.</p>
                        </main>
                      );
                    }
                    """, 0),
            editableFile("src/App.css", "App.css", """
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
                    """, 1),
            editableFile("src/main.tsx", "main.tsx", """
                    import React from 'react';
                    import ReactDOM from 'react-dom/client';
                    import App from './App';
                    import './index.css';

                    ReactDOM.createRoot(document.getElementById('root')!).render(
                      <React.StrictMode>
                        <App />
                      </React.StrictMode>
                    );
                    """, 2)
    );

    private final PreviewStorageService previewStorageService;

    @Override
    public boolean supports(ExecutionLanguage language) {
        return language == ExecutionLanguage.REACT;
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
            log.error("React build error", exception);
            return FrontendBuildResult.error(exception.getMessage());
        }
    }

    @Override
    public Path createWorkspace(List<EditableCodeFileDto> files) throws IOException {
        validateEditableFiles(files);
        Path workspaceDir = Files.createTempDirectory("react-workspace-");
        Path templateRoot = resolveTemplateRoot();
        log.info("React workspace initializing with templateRoot={} workspaceDir={} editableFileCount={}",
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
            log.info("React warm build starting in persistent workspace={}", workspaceDir);
            return runBuild(workspaceDir, timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS);
        } catch (Exception exception) {
            log.error("React build error in persistent workspace {}", workspaceDir, exception);
            return FrontendBuildResult.error(exception.getMessage());
        }
    }

    @Override
    public void destroyWorkspace(Path workspaceDir) {
        cleanupWorkspace(workspaceDir);
    }

    @Override
    public Process startWatchProcess(Path workspaceDir) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(List.of("npm", "run", "build", "--", "--watch"));
        processBuilder.directory(workspaceDir.toFile());
        processBuilder.redirectErrorStream(false);
        return processBuilder.start();
    }

    @Override
    public Path resolveWorkspacePreviewRoot(Path workspaceDir) {
        return workspaceDir.resolve("dist");
    }

    private void validateEditableFiles(List<EditableCodeFileDto> files) {
        List<EditableCodeFileDto> effectiveFiles = files == null || files.isEmpty() ? DEFAULT_FILES : files;
        if (effectiveFiles.size() > MAX_EDITABLE_FILES) {
            throw new IllegalArgumentException("Too many editable React files were provided");
        }
        int totalBytes = 0;
        Set<String> seenPaths = new java.util.LinkedHashSet<>();
        for (EditableCodeFileDto file : effectiveFiles) {
            String path = file.getPath();
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("Every editable React file must include a path");
            }
            String normalized = path.replace('\\', '/');
            if (!seenPaths.add(normalized)) {
                throw new IllegalArgumentException("Duplicate React workspace path: " + path);
            }
            if (!normalized.startsWith("src/")) {
                throw new IllegalArgumentException("Only files under src are editable in the React sandbox");
            }
            if (normalized.contains("..") || normalized.startsWith("/")) {
                throw new IllegalArgumentException("Invalid React workspace path: " + path);
            }
            boolean suffixAllowed = ALLOWED_FILE_SUFFIXES.stream().anyMatch(normalized::endsWith);
            if (!suffixAllowed) {
                throw new IllegalArgumentException("Only .tsx, .ts, and .css files are supported in the React sandbox");
            }
            int fileBytes = (file.getContent() == null ? 0 : file.getContent().getBytes(StandardCharsets.UTF_8).length);
            if (fileBytes > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("React file is too large: " + path);
            }
            totalBytes += fileBytes;
        }
        if (totalBytes > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("React workspace content is too large");
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

        throw new IllegalStateException("React template workspace is not available");
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
                if (relative.startsWith("dist") || relative.startsWith(".vite")) {
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
        ProcessBuilder processBuilder = new ProcessBuilder(List.of("npm", "run", "build"));
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
            log.info("React build completed successfully workspaceDir={} previewRoot={} previewId={}",
                    workspaceDir, previewRoot, previewId);
            return FrontendBuildResult.success(stdout, stderr, exitCode, executionTimeMs, "/workspace/preview/" + previewId + "/");
        } catch (ExecutionException exception) {
            return FrontendBuildResult.error("Failed during React build output reading: " + exception.getCause().getMessage());
        } catch (TimeoutException exception) {
            return FrontendBuildResult.error("Failed to read React build output");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return FrontendBuildResult.error("React build interrupted");
        }
    }

    private Path resolvePreviewRoot(Path workspaceDir) throws IOException {
        Path distRoot = workspaceDir.resolve("dist");
        if (Files.exists(distRoot.resolve("index.html"))) {
            return distRoot;
        }

        try (Stream<Path> paths = Files.walk(distRoot)) {
            return paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals("index.html"))
                    .map(Path::getParent)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("React preview output was not found after build"));
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
                    return normalized.contains("error") || normalized.contains("failed") || normalized.contains("vite") || normalized.contains("ts");
                })
                .forEach(errors::add);
        if (errors.isEmpty()) {
            if (stderr != null && !stderr.isBlank()) {
                return stderr.lines().filter(line -> !line.isBlank()).toList();
            }
            if (stdout != null && !stdout.isBlank()) {
                return stdout.lines().filter(line -> !line.isBlank()).toList();
            }
            return List.of("React build failed");
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
            log.warn("Failed to clean React workspace {}", workspaceDir, exception);
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
