package com.wallo.auth.service;

import com.wallo.auth.domain.User;
import com.wallo.auth.dto.request.LoginRequest;
import com.wallo.auth.dto.request.SignupRequest;
import com.wallo.auth.dto.response.AuthUserResponse;
import com.wallo.auth.exception.AuthErrorCode;
import com.wallo.auth.exception.AuthException;
import com.wallo.auth.mapper.AuthMapper;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$");

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(AuthMapper authMapper, PasswordEncoder passwordEncoder) {
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public AuthUserResponse signup(SignupRequest request) {
        validateSignupRequest(request);

        String email = normalizeEmail(request.getEmail());
        String name = request.getName().trim();
        String nickname = request.getNickname().trim();

        if (authMapper.countByEmail(email) > 0) {
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (authMapper.countByNickname(nickname) > 0) {
            throw new AuthException(AuthErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(nickname);
        user.setName(name);

        if (authMapper.insertUser(user) != 1 || user.getId() == null) {
            throw new IllegalStateException("사용자 저장에 실패했습니다.");
        }

        return AuthUserResponse.from(authMapper.findById(user.getId()));
    }

    @Override
    public AuthUserResponse login(LoginRequest request) {
        if (request == null || isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_REQUEST);
        }

        User user = authMapper.findByEmail(normalizeEmail(request.getEmail()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException(AuthErrorCode.LOGIN_FAILED);
        }

        boolean firstLogin = authMapper.markFirstLoginComplete(user.getId()) == 1;
        return AuthUserResponse.from(user, firstLogin);
    }

    @Override
    public AuthUserResponse getCurrentUser(Long userId) {
        if (userId == null) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED);
        }

        User user = authMapper.findById(userId);
        if (user == null) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED);
        }
        return AuthUserResponse.from(user);
    }

    private void validateSignupRequest(SignupRequest request) {
        if (request == null
                || isBlank(request.getName())
                || isBlank(request.getNickname())
                || isBlank(request.getEmail())
                || isBlank(request.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_REQUEST);
        }

        String name = request.getName().trim();
        String nickname = request.getNickname().trim();
        String email = normalizeEmail(request.getEmail());

        if (name.length() > 50 || nickname.length() > 50 || email.length() > 100) {
            throw new AuthException(AuthErrorCode.INVALID_REQUEST);
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new AuthException(AuthErrorCode.INVALID_EMAIL);
        }
        if (request.getPassword().length() < 8 || request.getPassword().length() > 72) {
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
