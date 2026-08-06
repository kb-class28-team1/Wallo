package com.wallo.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.user.dto.NicknameDto;
import com.wallo.user.exception.UserErrorCode;
import com.wallo.user.exception.UserException;
import com.wallo.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserProfileServiceTest {

    private UserMapper userMapper;
    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userProfileService = new UserProfileService(userMapper);
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

    private NicknameDto.UpdateRequest request(String nickname) {
        NicknameDto.UpdateRequest request = new NicknameDto.UpdateRequest();
        request.setNickname(nickname);
        return request;
    }
}
