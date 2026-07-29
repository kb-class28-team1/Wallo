package com.wallo.challenge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wallo.challenge.domain.Challenge;
import com.wallo.challenge.dto.request.CreateChallengeRequest;
import com.wallo.challenge.dto.response.CreateChallengeResponse;
import com.wallo.challenge.exception.AlreadyJoinedChallengeException;
import com.wallo.challenge.mapper.ChallengeMapper;
import org.junit.jupiter.api.Test;

class ChallengeServiceImplTest {

    @Test
    void createsChallengeAndConnectsOwnerWhenOwnerHasNoCurrentChallenge() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        ChallengeService service = new ChallengeServiceImpl(mapper);

        CreateChallengeResponse response = service.createChallenge(1L, request("7월 절약", "SAVING"));

        assertEquals(1L, response.getId());
        assertEquals("7월 절약", response.getName());
        assertEquals("SAVING", response.getChallengeType());
        assertEquals("ACTIVE", response.getStatus());
        assertNotNull(response.getInviteCode());
        assertEquals(8, response.getInviteCode().length());
        assertEquals(1L, mapper.currentChallengeId);
    }

    @Test
    void throwsExceptionWhenOwnerAlreadyHasCurrentChallenge() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.currentChallengeId = 10L;
        ChallengeService service = new ChallengeServiceImpl(mapper);

        assertThrows(
                AlreadyJoinedChallengeException.class,
                () -> service.createChallenge(1L, request("새 챌린지", "SAVING")));
    }

    @Test
    void throwsExceptionWhenAnotherRequestConnectsOwnerFirst() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.updateResult = 0;
        ChallengeService service = new ChallengeServiceImpl(mapper);

        assertThrows(
                AlreadyJoinedChallengeException.class,
                () -> service.createChallenge(1L, request("새 챌린지", "SAVING")));
    }

    private CreateChallengeRequest request(String name, String challengeType) {
        CreateChallengeRequest request = new CreateChallengeRequest();
        request.setName(name);
        request.setChallengeType(challengeType);
        return request;
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Mapper다. */
    private static class FakeChallengeMapper implements ChallengeMapper {

        private Long currentChallengeId;
        private int updateResult = 1;
        private Challenge savedChallenge;

        @Override
        public int insertChallenge(Challenge challenge) {
            challenge.setId(1L);
            savedChallenge = challenge;
            return 1;
        }

        @Override
        public Challenge findChallengeById(Long challengeId) {
            return savedChallenge;
        }

        @Override
        public Long findCurrentChallengeIdByUserId(Long userId) {
            return currentChallengeId;
        }

        @Override
        public int updateCurrentChallengeId(Long userId, Long challengeId) {
            if (updateResult == 1) {
                currentChallengeId = challengeId;
            }
            return updateResult;
        }

        @Override
        public int countByInviteCode(String inviteCode) {
            return 0;
        }
    }
}
