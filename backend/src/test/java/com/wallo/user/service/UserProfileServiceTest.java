package com.wallo.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.user.dto.NicknameDto;
import com.wallo.user.dto.PasswordDto;
import com.wallo.user.dto.UserProfileDto;
import com.wallo.user.exception.UserErrorCode;
import com.wallo.user.exception.UserException;
import com.wallo.user.mapper.UserMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserProfileServiceTest {

    private UserMapper userMapper;
    private UserProfileService userProfileService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = new BCryptPasswordEncoder();
        userProfileService = new UserProfileService(userMapper, passwordEncoder);
    }

    @Test
    void returnsProfileForExistingUser() {
        when(userMapper.findProfile(7L)).thenReturn(
                new UserProfileDto.Response(
                        7L,
                        "김혜진",
                        "저축왕 펭귄",
                        "user@wallo.test",
                        "/api/profile-images/profile.png"
                )
        );

        UserProfileDto.Response response = userProfileService.getProfile(7L);

        assertEquals(7L, response.getId());
        assertEquals("김혜진", response.getName());
        assertEquals("저축왕 펭귄", response.getNickname());
        assertEquals("user@wallo.test", response.getEmail());
        assertEquals("/api/profile-images/profile.png", response.getProfileImageUrl());
    }

    @Test
    void rejectsProfileLookupWhenUserDoesNotExist() {
        UserException exception = assertThrows(
                UserException.class,
                () -> userProfileService.getProfile(7L)
        );

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updatesNicknameAfterTrimmingInput() {
        when(userMapper.countByNicknameExceptUserId("new-nickname", 7L)).thenReturn(0);
        when(userMapper.updateNickname(7L, "new-nickname")).thenReturn(1);

        NicknameDto.Response response = userProfileService.updateNickname(
                7L,
                request(" new-nickname ")
        );

        assertEquals("new-nickname", response.getNickname());
        verify(userMapper).updateNickname(7L, "new-nickname");
    }

    @Test
    void rejectsBlankNickname() {
        UserException exception = assertThrows(
                UserException.class,
                () -> userProfileService.updateNickname(7L, request("   "))
        );

        assertEquals(UserErrorCode.INVALID_NICKNAME, exception.getErrorCode());
    }

    @Test
    void rejectsDuplicateNickname() {
        when(userMapper.countByNicknameExceptUserId("taken", 7L)).thenReturn(1);

        UserException exception = assertThrows(
                UserException.class,
                () -> userProfileService.updateNickname(7L, request("taken"))
        );

        assertEquals(UserErrorCode.NICKNAME_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void changesPasswordAfterVerifyingCurrentPassword() {
        String currentPassword = "current123!";
        String newPassword = "newPassword123!";
        when(userMapper.findPasswordHash(7L)).thenReturn(passwordEncoder.encode(currentPassword));
        when(userMapper.updatePasswordHash(eq(7L), anyString())).thenReturn(1);

        userProfileService.changePassword(
                7L,
                passwordRequest(currentPassword, newPassword, newPassword)
        );

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePasswordHash(eq(7L), passwordCaptor.capture());
        assertTrue(passwordEncoder.matches(newPassword, passwordCaptor.getValue()));
    }

    @Test
    void rejectsIncorrectCurrentPassword() {
        when(userMapper.findPasswordHash(7L)).thenReturn(passwordEncoder.encode("current123!"));

        UserException exception = assertThrows(
                UserException.class,
                () -> userProfileService.changePassword(
                        7L,
                        passwordRequest("wrong123!", "newPassword123!", "newPassword123!")
                )
        );

        assertEquals(UserErrorCode.CURRENT_PASSWORD_MISMATCH, exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getErrorCode().getStatus());
    }

    @Test
    void rejectsMismatchedNewPasswordConfirmation() {
        UserException exception = assertThrows(
                UserException.class,
                () -> userProfileService.changePassword(
                        7L,
                        passwordRequest("current123!", "newPassword123!", "different123!")
                )
        );

        assertEquals(UserErrorCode.PASSWORD_CONFIRMATION_MISMATCH, exception.getErrorCode());
    }

    @Test
    void rejectsNewPasswordShorterThanEightCharacters() {
        UserException exception = assertThrows(
                UserException.class,
                () -> userProfileService.changePassword(
                        7L,
                        passwordRequest("current123!", "short", "short")
                )
        );

        assertEquals(UserErrorCode.INVALID_PASSWORD, exception.getErrorCode());
    }

    private NicknameDto.UpdateRequest request(String nickname) {
        NicknameDto.UpdateRequest request = new NicknameDto.UpdateRequest();
        request.setNickname(nickname);
        return request;
    }

    private PasswordDto.ChangeRequest passwordRequest(
            String currentPassword,
            String newPassword,
            String newPasswordConfirm) {
        PasswordDto.ChangeRequest request = new PasswordDto.ChangeRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        request.setNewPasswordConfirm(newPasswordConfirm);
        return request;
    }
}
