package com.wallo.asset.mapper;

import com.wallo.asset.dto.ConnectionDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ConnectionMapper {

    int insertConnections(
            @Param("connections") List<ConnectionDto.Result> results,
            @Param("userId") long userId,
            @Param("loginType") String loginType,
            @Param("loginId") String loginId,
            @Param("loginPassword") String loginPassword
    );
}



