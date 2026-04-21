package com.altimetrik.interview.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PreviewStorageService {

    private static final Duration DEFAULT_RETENTION = Duration.ofHours(6);

    private final Path previewRoot;

    public PreviewStorageService(@Value("${app.preview.root:}") String configuredRoot) {
        String resolvedRoot = configuredRoot == null || configuredRoot.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "interview-frontend-previews").toString()
                : configuredRoot;
        this.previewRoot = Path.of(resolvedRoot);
    }

    public String storePreview(Path sourceDir) throws IOException {
        cleanupExpiredPreviews();
        Files.createDirectories(previewRoot);

        String previewId = UUID.randomUUID().toString();
        Path targetDir = previewRoot.resolve(previewId);
        copyDirectory(sourceDir, targetDir);
        return previewId;
    }

    public Resource resolvePreviewResource(String previewId, String relativePath) throws IOException {
        Path previewDir = previewRoot.resolve(previewId).normalize();
        if (!previewDir.startsWith(previewRoot) || !Files.exists(previewDir)) {
            return null;
        }

        return resolveResource(previewDir, relativePath);
    }

    public byte[] createPreviewArchive(String previewId) throws IOException {
        Path previewDir = previewRoot.resolve(previewId).normalize();
        if (!previewDir.startsWith(previewRoot) || !Files.exists(previewDir) || !Files.isDirectory(previewDir)) {
            return null;
        }

        return createArchive(previewDir);
    }

    public byte[] createLivePreviewArchive(Path previewDir) throws IOException {
        if (previewDir == null) {
            return null;
        }
        Path normalizedPreviewDir = previewDir.normalize();
        if (!Files.exists(normalizedPreviewDir) || !Files.isDirectory(normalizedPreviewDir)) {
            return null;
        }

        return createArchive(normalizedPreviewDir);
    }

    private byte[] createArchive(Path previewDir) throws IOException {
        try (java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
             ZipOutputStream zipStream = new ZipOutputStream(byteStream);
             Stream<Path> paths = Files.walk(previewDir)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .sorted()
                    .forEach(path -> writeZipEntry(previewDir, path, zipStream));
            zipStream.finish();
            return byteStream.toByteArray();
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    public Resource resolveLivePreviewResource(Path previewDir, String relativePath) throws IOException {
        if (previewDir == null) {
            return null;
        }
        Path normalizedRoot = previewDir.normalize();
        if (!Files.exists(normalizedRoot)) {
            return null;
        }

        return resolveResource(normalizedRoot, relativePath);
    }

    private Resource resolveResource(Path previewDir, String relativePath) throws IOException {
        if (!Files.exists(previewDir)) {
            return null;
        }

        String normalizedRelativePath = relativePath == null || relativePath.isBlank() ? "index.html" : relativePath;
        Path resourcePath = previewDir.resolve(normalizedRelativePath).normalize();
        if (!resourcePath.startsWith(previewDir)) {
            return null;
        }
        if (!Files.exists(resourcePath) || Files.isDirectory(resourcePath)) {
            if (!normalizedRelativePath.contains(".")) {
                Path fallbackIndex = previewDir.resolve("index.html");
                if (Files.exists(fallbackIndex) && !Files.isDirectory(fallbackIndex)) {
                    return new FileSystemResource(fallbackIndex);
                }
            }
            return null;
        }
        return new FileSystemResource(resourcePath);
    }

    public String detectContentType(Resource resource) throws IOException {
        if (!(resource instanceof FileSystemResource fileSystemResource)) {
            return null;
        }
        Path path = fileSystemResource.getFile().toPath();
        return Files.probeContentType(path);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(Objects.requireNonNull(destination.getParent()));
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void cleanupExpiredPreviews() {
        if (!Files.exists(previewRoot)) {
            return;
        }

        Instant cutoff = Instant.now().minus(DEFAULT_RETENTION);
        try (Stream<Path> children = Files.list(previewRoot)) {
            for (Path child : children.toList()) {
                try {
                    Instant lastModified = Files.getLastModifiedTime(child).toInstant();
                    if (lastModified.isBefore(cutoff)) {
                        deleteRecursively(child);
                    }
                } catch (IOException exception) {
                    log.warn("Failed to inspect preview directory {}", child, exception);
                }
            }
        } catch (IOException exception) {
            log.warn("Failed to clean preview storage {}", previewRoot, exception);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void writeZipEntry(Path previewDir, Path file, ZipOutputStream zipStream) {
        Path relativePath = previewDir.relativize(file);
        String zipEntryName = relativePath.toString().replace('\\', '/');
        try {
            zipStream.putNextEntry(new ZipEntry(zipEntryName));
            Files.copy(file, zipStream);
            zipStream.closeEntry();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to archive preview asset " + file, exception);
        }
    }
}
