package com.wallo.mission.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class StoredFeedEvidenceLoader implements MissionEvidenceLoader {
    private static final String URL_PREFIX = "/api/feed-media/";
    private static final long MAX_SIZE = 50L * 1024 * 1024;

    @Override
    public Evidence load(String mediaUrl, String mediaType) {
        if (mediaUrl == null || !mediaUrl.startsWith(URL_PREFIX)) {
            throw new IllegalArgumentException("Unsupported feed media URL.");
        }
        String filename = mediaUrl.substring(URL_PREFIX.length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid feed media path.");
        }
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "wallo-feed-media")
                .toAbsolutePath().normalize();
        Path file = directory.resolve(filename).normalize();
        if (!file.getParent().equals(directory) || !Files.isRegularFile(file)) {
            throw new IllegalStateException("Feed media file was not found.");
        }
        try {
            if (Files.size(file) > MAX_SIZE) {
                throw new IllegalArgumentException("Mission evidence must not exceed 50MB.");
            }
            String contentType = "VIDEO".equals(mediaType) ? "video/mp4" : "image/jpeg";
            return new Evidence(Files.readAllBytes(file), contentType);
        } catch (IOException exception) {
            throw new IllegalStateException("Feed media file could not be read.", exception);
        }
    }
}
