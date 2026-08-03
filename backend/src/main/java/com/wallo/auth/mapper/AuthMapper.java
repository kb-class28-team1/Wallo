package com.wallo.auth.mapper;

import com.wallo.auth.domain.User;
import org.apache.ibatis.annotations.Param;

public interface AuthMapper {

    User findByEmail(@Param("email") String email);

    User findById(@Param("id") Long id);

    int countByEmail(@Param("email") String email);

    int countByNickname(@Param("nickname") String nickname);

    int insertUser(User user);

    int markFirstLoginComplete(@Param("id") Long id);

    int countActiveConnections(@Param("userId") Long userId);
}
