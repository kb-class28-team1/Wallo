package com.wallo.user.controller;

import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileImageController {

    @GetMapping("/api/profile-images/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        String safeName = Path.of(filename).getFileName().toString();
        Resource resource = new FileSystemResource(
                Path.of(System.getProperty("java.io.tmpdir"), "wallo-profile-images", safeName));

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }
}
