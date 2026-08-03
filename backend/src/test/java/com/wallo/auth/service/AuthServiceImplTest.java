package com.wallo.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.auth.domain.User;
import com.wallo.auth.dto.request.LoginRequest;
import com.wallo.auth.dto.request.SignupRequest;
import com.wallo.auth.dto.response.AuthUserResponse;
import com.wallo.auth.exception.AuthErrorCode;
import com.wallo.auth.exception.AuthException;
import com.wallo.auth.mapper.AuthMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void signsUpWithNormalizedEmailAndEncryptedPassword() {
        FakeAuthMapper mapper = new FakeAuthMapper();
        AuthService service = new AuthServiceImpl(mapper, passwordEncoder);

        AuthUserResponse response =
                service.signup(signupRequest(" 홍길동 ", " wallo ", " Test@Wallo.com ", "password123!"));

        assertEquals(1L, response.getId());
        assertEquals("test@wallo.com", response.getEmail());
        assertEquals("홍길동", response.getName());
        assertEquals("wallo", response.getNickname());
        assertNotEquals("password123!", mapper.savedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches("password123!", mapper.savedUser.getPasswordHash()));
    }

    @Test
    void rejectsDuplicateEmail() {
        FakeAuthMapper mapper = new FakeAuthMapper();
        mapper.emailCount = 1;
        AuthService service = new AuthServiceImpl(mapper, passwordEncoder);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> service.signup(
                        signupRequest("홍길동", "wallo", "test@wallo.com", "password123!")));

        assertEquals(AuthErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        assertFalse(mapper.insertCalled);
    }

    @Test
    void rejectsDuplicateNickname() {
        FakeAuthMapper mapper = new FakeAuthMapper();
        mapper.nicknameCount = 1;
        AuthService service = new AuthServiceImpl(mapper, passwordEncoder);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> service.signup(
                        signupRequest("홍길동", "wallo", "test@wallo.com", "password123!")));

        assertEquals(AuthErrorCode.NICKNAME_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void logsInWithCorrectPassword() {
        FakeAuthMapper mapper = new FakeAuthMapper();
        mapper.savedUser = user(10L, "test@wallo.com", passwordEncoder.encode("password123!"));
        AuthService service = new AuthServiceImpl(mapper, passwordEncoder);

        AuthUserResponse response = service.login(loginRequest("TEST@WALLO.COM", "password123!"));

        assertEquals(10L, response.getId());
        assertEquals("test@wallo.com", response.getEmail());
        assertTrue(response.isFirstLogin());
    }

    @Test
    void doesNotTreatSubsequentLoginsAsFirstLogin() {
        FakeAuthMapper mapper = new FakeAuthMapper();
        mapper.savedUser = user(10L, "test@wallo.com", passwordEncoder.encode("password123!"));
        AuthService service = new AuthServiceImpl(mapper, passwordEncoder);

        service.login(loginRequest("test@wallo.com", "password123!"));
        AuthUserResponse response = service.login(loginRequest("test@wallo.com", "password123!"));

        assertFalse(response.isFirstLogin());
    }

    @Test
    void includesConnectionCompletionInLoginResponse() {
        FakeAuthMapper mapper = new FakeAuthMapper();
        mapper.savedUser = user(10L, "test@wallo.com", passwordEncoder.encode("password123!"));
        mapper.activeConnectionCount = 1;
        AuthService service = new AuthServiceImpl(mapper, passwordEncoder);

        AuthUserResponse response = service.login(loginRequest("test@wallo.com", "password123!"));

        assertTrue(response.isConnectionCompleted());
    }

    @Test
    void rejectsWrongPasswordWithoutRevealingWhetherUserExists() {
        FakeAuthMapper mapper = new FakeAuthMapper();
        mapper.savedUser = user(10L, "test@wallo.com", passwordEncoder.encode("password123!"));
        AuthService service = new AuthServiceImpl(mapper, passwordEncoder);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> service.login(loginRequest("test@wallo.com", "wrong-password")));

        assertEquals(AuthErrorCode.LOGIN_FAILED, exception.getErrorCode());
        assertEquals("이메일 또는 비밀번호가 올바르지 않습니다.", exception.getMessage());
    }

    @Test
    void rejectsMissingCurrentUser() {
        AuthService service = new AuthServiceImpl(new FakeAuthMapper(), passwordEncoder);

        AuthException exception =
                assertThrows(AuthException.class, () -> service.getCurrentUser(999L));

        assertEquals(AuthErrorCode.AUTH_REQUIRED, exception.getErrorCode());
    }

    private SignupRequest signupRequest(
            String name, String nickname, String email, String password) {
        SignupRequest request = new SignupRequest();
        request.setName(name);
        request.setNickname(nickname);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private User user(Long id, String email, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setName("홍길동");
        user.setNickname("wallo");
        user.setProfileImageUrl("/images/profiles/default-profile.svg");
        user.setPoint(0);
        return user;
    }

    private static class FakeAuthMapper implements AuthMapper {

        private final Map<Long, User> users = new HashMap<>();
        private int emailCount;
        private int nicknameCount;
        private boolean insertCalled;
        private User savedUser;
        private int activeConnectionCount;

        @Override
        public User findByEmail(String email) {
            if (savedUser != null && savedUser.getEmail().equals(email)) {
                return savedUser;
            }
            return null;
        }

        @Override
        public User findById(Long id) {
            if (savedUser != null && savedUser.getId().equals(id)) {
                return savedUser;
            }
            return users.get(id);
        }

        @Override
        public int countByEmail(String email) {
            return emailCount;
        }

        @Override
        public int countByNickname(String nickname) {
            return nicknameCount;
        }

        @Override
        public int insertUser(User user) {
            insertCalled = true;
            user.setId(1L);
            user.setProfileImageUrl("/images/profiles/default-profile.svg");
            user.setPoint(0);
            savedUser = user;
            users.put(user.getId(), user);
            return 1;
        }

        @Override
        public int markFirstLoginComplete(Long id) {
            if (savedUser == null || !savedUser.getId().equals(id) || savedUser.isHasLoggedIn()) {
                return 0;
            }
            savedUser.setHasLoggedIn(true);
            return 1;
        }

        @Override
        public int countActiveConnections(Long userId) {
            return activeConnectionCount;
        }
    }
}
