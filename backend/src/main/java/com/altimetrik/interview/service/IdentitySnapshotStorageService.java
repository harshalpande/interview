package com.altimetrik.interview.service;

import com.altimetrik.interview.enums.ParticipantRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class IdentitySnapshotStorageService {

    private final Path storageRoot;

    public IdentitySnapshotStorageService(@Value("${app.storage.root}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public String storeSnapshot(String sessionId, ParticipantRole role, String originalFilename, Path tempFile) throws IOException {
        String extension = resolveExtension(originalFilename);
        Path sessionDirectory = storageRoot.resolve("identity-snapshots").resolve(sessionId);
        Files.createDirectories(sessionDirectory);

        String filename = role.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + extension;
        Path target = sessionDirectory.resolve(filename).normalize();
        Files.copy(tempFile, target, StandardCopyOption.REPLACE_EXISTING);

        return storageRoot.relativize(target).toString().replace('\\', '/');
    }

    public Resource loadAsResource(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path resolved = storageRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(storageRoot) || !Files.exists(resolved)) {
            return null;
        }
        return new FileSystemResource(resolved);
    }

    public void deleteIfExists(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path resolved = storageRoot.resolve(relativePath).normalize();
        if (resolved.startsWith(storageRoot)) {
            Files.deleteIfExists(resolved);
        }
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ".jpg";
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case ".jpg", ".jpeg", ".png", ".webp" -> extension;
            default -> ".jpg";
        };
    }
}
