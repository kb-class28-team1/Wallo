package com.wallo.user.service;

import com.wallo.user.dto.NicknameDto;
import com.wallo.user.exception.UserErrorCode;
import com.wallo.user.exception.UserException;
import com.wallo.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private static final int MAX_NICKNAME_LENGTH = 50;

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
}
