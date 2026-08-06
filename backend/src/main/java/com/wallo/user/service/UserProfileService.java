package com.wallo.user.service;

import com.wallo.user.dto.NicknameDto;
import com.wallo.user.dto.PasswordDto;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UserProfileService {

    private static final int MAX_NICKNAME_LENGTH = 50;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final long MAX_PROFILE_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final String DEFAULT_PROFILE_IMAGE_URL = "/images/profiles/default-profile.svg";
    private static final String PROFILE_IMAGE_URL_PREFIX = "/api/profile-images/";
    private static final Set<String> ALLOWED_PROFILE_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png"
    );

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Path profileImageStorageDirectory;

    @Autowired
    public UserProfileService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            @Value("${profile.image.storage-dir:${user.home}/Documents/Wallo-data/profile-images}")
            String profileImageStorageDirectory
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.profileImageStorageDirectory = Path.of(profileImageStorageDirectory)
                .toAbsolutePath()
                .normalize();
    }

    // 단위 테스트와 스프링 외부에서 서비스를 생성하는 코드를 위한 기본 경로다.
    public UserProfileService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this(
                userMapper,
                passwordEncoder,
                Path.of(
                        System.getProperty("user.home"),
                        "Documents",
                        "Wallo-data",
                        "profile-images"
                ).toString()
        );
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
    public void changePassword(long userId, PasswordDto.ChangeRequest request) {
        if (request == null
                || isBlank(request.getCurrentPassword())
                || isBlank(request.getNewPassword())) {
            throw new UserException(UserErrorCode.INVALID_PASSWORD);
        }

        String newPassword = request.getNewPassword();
        if (newPassword.length() < MIN_PASSWORD_LENGTH
                || newPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new UserException(UserErrorCode.INVALID_PASSWORD);
        }

        if (!newPassword.equals(request.getNewPasswordConfirm())) {
            throw new UserException(UserErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }

        String currentPasswordHash = userMapper.findPasswordHash(userId);
        if (currentPasswordHash == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!passwordMatches(request.getCurrentPassword(), currentPasswordHash)) {
            throw new UserException(UserErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        if (passwordMatches(newPassword, currentPasswordHash)) {
            throw new UserException(UserErrorCode.PASSWORD_SAME_AS_CURRENT);
        }

        if (userMapper.updatePasswordHash(userId, passwordEncoder.encode(newPassword)) == 0) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
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

    private boolean passwordMatches(String rawPassword, String passwordHash) {
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
        return profileImageStorageDirectory;
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
