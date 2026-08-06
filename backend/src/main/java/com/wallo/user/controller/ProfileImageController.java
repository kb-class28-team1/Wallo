package com.wallo.user.controller;

import com.wallo.auth.CurrentUserProvider;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
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

    private final CurrentUserProvider currentUserProvider;
    private final Path profileImageStorageDirectory;

    public ProfileImageController(
            CurrentUserProvider currentUserProvider,
            @Value("${profile.image.storage-dir:${user.home}/Documents/Wallo-data/profile-images}")
            String profileImageStorageDirectory
    ) {
        this.currentUserProvider = currentUserProvider;
        this.profileImageStorageDirectory = Path.of(profileImageStorageDirectory)
                .toAbsolutePath()
                .normalize();
    }

    @GetMapping("/api/profile-images/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        // 프로필 이미지는 랭킹·피드에 노출되므로 로그인 사용자 전체가 조회할 수 있다.
        currentUserProvider.getCurrentUserId();

        String safeName = Path.of(filename).getFileName().toString();
        Path imagePath = profileImageStorageDirectory.resolve(safeName).normalize();
        if (!profileImageStorageDirectory.equals(imagePath.getParent())) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(imagePath);

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
