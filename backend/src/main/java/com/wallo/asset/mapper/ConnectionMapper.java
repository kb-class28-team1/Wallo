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

    Long findActiveConnectionId(
            @Param("userId") long userId,
            @Param("institutionId") Long institutionId
    );

    List<ConnectionDto.ConnectedAsset> findConnectedAssets(@Param("userId") long userId);

    int softDeleteConnection(
            @Param("userId") long userId,
            @Param("connectionId") long connectionId
    );
}



