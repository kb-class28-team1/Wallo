package com.wallo.user.mapper;

import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    int countByNicknameExceptUserId(
            @Param("nickname") String nickname,
            @Param("userId") Long userId
    );

    int updateNickname(
            @Param("userId") Long userId,
            @Param("nickname") String nickname
    );
}
