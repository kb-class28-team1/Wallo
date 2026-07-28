package com.wallo.challenge.mapper;

import org.apache.ibatis.annotations.Param;
import com.wallo.challenge.domain.Challenge;

/**
 * challenge 및 users 테이블에 접근하는 MyBatis Mapper.
 * 챌린지 생성 서비스에서 필요한 조회·저장·사용자 참여 상태 변경을 제공한다.
 */
public interface ChallengeMapper {

    /** 챌린지를 저장하고, DB가 생성한 PK를 challenge.id에 채운다. */
    int insertChallenge(Challenge challenge);

    /** 생성 완료 후 응답에 사용할 챌린지 정보를 조회한다. */
    Challenge findChallengeById(@Param("challengeId") Long challengeId);

    /** 사용자가 이미 참여 중인 챌린지가 있는지 확인한다. */
    Long findCurrentChallengeIdByUserId(@Param("userId") Long userId);

    /** 사용자에게 현재 참여 챌린지를 연결한다. */
    int updateCurrentChallengeId(
            @Param("userId") Long userId,
            @Param("challengeId") Long challengeId);

    /** 초대 코드 생성 시 중복 여부를 확인한다. */
    int countByInviteCode(@Param("inviteCode") String inviteCode);
}
