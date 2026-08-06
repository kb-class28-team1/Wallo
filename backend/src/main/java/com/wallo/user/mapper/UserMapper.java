package com.wallo.user.mapper;

import com.wallo.user.dto.UserProfileDto;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    UserProfileDto.Response findProfile(@Param("userId") Long userId);

    int countByNicknameExceptUserId(
            @Param("nickname") String nickname,
            @Param("userId") Long userId
    );

    int updateNickname(
            @Param("userId") Long userId,
            @Param("nickname") String nickname
    );

    String findPasswordHash(@Param("userId") Long userId);

    int updatePasswordHash(
            @Param("userId") Long userId,
            @Param("passwordHash") String passwordHash
    );

    String findProfileImageUrl(@Param("userId") Long userId);

    int updateProfileImageUrl(
            @Param("userId") Long userId,
            @Param("profileImageUrl") String profileImageUrl
    );
}
