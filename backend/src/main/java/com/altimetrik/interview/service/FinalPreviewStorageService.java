package com.altimetrik.interview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FinalPreviewStorageService {

    private final Path storageRoot;

    public FinalPreviewStorageService(@Value("${app.storage.root}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public String storePreviewArchive(String sessionId, byte[] archiveBytes) throws IOException {
        if (archiveBytes == null || archiveBytes.length == 0) {
            return null;
        }

        Path previewDirectory = storageRoot.resolve("final-previews").resolve(sessionId).normalize();
        if (!previewDirectory.startsWith(storageRoot)) {
            throw new IOException("Resolved preview directory is outside the configured storage root");
        }

        deleteDirectory(previewDirectory);
        Files.createDirectories(previewDirectory);
        unzipArchive(previewDirectory, archiveBytes);
        return storageRoot.relativize(previewDirectory).toString().replace('\\', '/');
    }

    public Resource loadPreviewResource(String relativePath, String assetPath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }

        Path previewRoot = storageRoot.resolve(relativePath).normalize();
        if (!previewRoot.startsWith(storageRoot) || !Files.exists(previewRoot)) {
            return null;
        }

        String normalizedAssetPath = (assetPath == null || assetPath.isBlank()) ? "index.html" : assetPath;
        Path resolved = previewRoot.resolve(normalizedAssetPath).normalize();
        if (!resolved.startsWith(previewRoot) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
            if (!normalizedAssetPath.contains(".")) {
                Path fallbackIndex = previewRoot.resolve("index.html").normalize();
                if (fallbackIndex.startsWith(previewRoot) && Files.exists(fallbackIndex) && !Files.isDirectory(fallbackIndex)) {
                    return new FileSystemResource(fallbackIndex);
                }
            }
            return null;
        }

        return new FileSystemResource(resolved);
    }

    public String detectContentType(Resource resource) throws IOException {
        if (!(resource instanceof FileSystemResource fileSystemResource)) {
            return null;
        }
        Path path = fileSystemResource.getFile().toPath();
        String detected = Files.probeContentType(path);
        if (detected != null && !detected.isBlank()) {
            return detected;
        }
        return fallbackContentType(path.getFileName().toString());
    }

    private String fallbackContentType(String fileName) {
        String normalized = fileName == null ? "" : fileName.toLowerCase();
        if (normalized.endsWith(".html")) {
            return "text/html";
        }
        if (normalized.endsWith(".js") || normalized.endsWith(".mjs")) {
            return "text/javascript";
        }
        if (normalized.endsWith(".css")) {
            return "text/css";
        }
        if (normalized.endsWith(".json") || normalized.endsWith(".map")) {
            return "application/json";
        }
        if (normalized.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private void unzipArchive(Path targetDirectory, byte[] archiveBytes) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zipInputStream.closeEntry();
                    continue;
                }

                Path targetPath = targetDirectory.resolve(entry.getName()).normalize();
                if (!targetPath.startsWith(targetDirectory)) {
                    throw new IOException("Preview archive contains an unsafe entry path");
                }

                Files.createDirectories(targetPath.getParent());
                Files.copy(zipInputStream, targetPath);
                zipInputStream.closeEntry();
            }
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
