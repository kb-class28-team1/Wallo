package com.wallo.feed.controller;

import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedMediaController {
    @GetMapping("/api/feed-media/{filename:.+}")
    public ResponseEntity<Resource> media(@PathVariable String filename) {
        String safeName = Path.of(filename).getFileName().toString();
        Resource resource = new FileSystemResource(
                Path.of(System.getProperty("java.io.tmpdir"), "wallo-feed-media", safeName));
        return resource.exists() ? ResponseEntity.ok(resource) : ResponseEntity.notFound().build();
    }
}
