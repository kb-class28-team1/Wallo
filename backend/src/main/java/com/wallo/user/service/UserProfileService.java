package com.wallo.user.service;

import com.wallo.user.dto.NicknameDto;
import com.wallo.user.dto.ProfileImageDto;
import com.wallo.user.exception.UserErrorCode;
import com.wallo.user.exception.UserException;
import com.wallo.user.mapper.UserMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private static final int MAX_NICKNAME_LENGTH = 50;
    private static final long MAX_PROFILE_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final String DEFAULT_PROFILE_IMAGE_URL = "/images/profiles/default-profile.svg";
    private static final String PROFILE_IMAGE_URL_PREFIX = "/api/profile-images/";
    private static final Set<String> ALLOWED_PROFILE_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png"
    );

    private final UserMapper userMapper;

    public UserProfileService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional
    public NicknameDto.Response updateNickname(long userId, NicknameDto.UpdateRequest request) {
        if (request == null || request.getNickname() == null) {
            throw new UserException(UserErrorCode.INVALID_NICKNAME);
        }

        String nickname = request.getNickname().trim();
        if (nickname.isEmpty() || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new UserException(UserErrorCode.INVALID_NICKNAME);
        }

        if (userMapper.countByNicknameExceptUserId(nickname, userId) > 0) {
            throw new UserException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        if (userMapper.updateNickname(userId, nickname) == 0) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }

        return new NicknameDto.Response(nickname);
    }

    @Transactional
    public ProfileImageDto.Response updateProfileImage(long userId, MultipartFile image) {
        validateProfileImage(image);

        String previousImageUrl = userMapper.findProfileImageUrl(userId);
        Path storedPath = null;
        try {
            storedPath = storeProfileImage(image);
            String profileImageUrl = PROFILE_IMAGE_URL_PREFIX + storedPath.getFileName();

            if (userMapper.updateProfileImageUrl(userId, profileImageUrl) == 0) {
                deleteStoredImage(storedPath);
                throw new UserException(UserErrorCode.USER_NOT_FOUND);
            }

            deleteStoredImage(previousImageUrl);
            return new ProfileImageDto.Response(profileImageUrl);
        } catch (UserException exception) {
            deleteStoredImage(storedPath);
            throw exception;
        } catch (IOException exception) {
            deleteStoredImage(storedPath);
            throw new UserException(UserErrorCode.PROFILE_IMAGE_STORAGE_FAILED);
        } catch (RuntimeException exception) {
            deleteStoredImage(storedPath);
            throw exception;
        }
    }

    @Transactional
    public ProfileImageDto.Response resetProfileImage(long userId) {
        String previousImageUrl = userMapper.findProfileImageUrl(userId);
        if (userMapper.updateProfileImageUrl(userId, DEFAULT_PROFILE_IMAGE_URL) == 0) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }

        deleteStoredImage(previousImageUrl);
        return new ProfileImageDto.Response(DEFAULT_PROFILE_IMAGE_URL);
    }

    private void validateProfileImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new UserException(UserErrorCode.INVALID_PROFILE_IMAGE);
        }
        if (image.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }

        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_PROFILE_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new UserException(UserErrorCode.INVALID_PROFILE_IMAGE);
        }

        try {
            try (InputStream inputStream = image.getInputStream()) {
                if (ImageIO.read(inputStream) == null) {
                    throw new UserException(UserErrorCode.INVALID_PROFILE_IMAGE);
                }
            }
        } catch (IOException exception) {
            throw new UserException(UserErrorCode.INVALID_PROFILE_IMAGE);
        }
    }

    private Path storeProfileImage(MultipartFile image) throws IOException {
        Path directory = profileImageDirectory();
        Files.createDirectories(directory);

        String extension = "image/png".equalsIgnoreCase(image.getContentType()) ? ".png" : ".jpg";
        Path destination = directory.resolve(UUID.randomUUID() + extension);
        try (InputStream inputStream = image.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return destination;
    }

    private Path profileImageDirectory() {
        return Path.of(System.getProperty("java.io.tmpdir"), "wallo-profile-images");
    }

    private void deleteStoredImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(PROFILE_IMAGE_URL_PREFIX)) {
            return;
        }

        String filename = imageUrl.substring(PROFILE_IMAGE_URL_PREFIX.length());
        Path path = profileImageDirectory().resolve(filename).normalize();
        if (!path.getParent().equals(profileImageDirectory())) {
            return;
        }

        deleteStoredImage(path);
    }

    private void deleteStoredImage(Path path) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 이전 이미지 삭제 실패가 새 프로필 이미지 저장 결과를 무효화하지 않도록 한다.
        }
    }
}
