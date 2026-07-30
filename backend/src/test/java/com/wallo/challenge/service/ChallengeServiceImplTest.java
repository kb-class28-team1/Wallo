package com.wallo.challenge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.wallo.challenge.domain.Challenge;
import com.wallo.challenge.domain.WeeklyRanking;
import com.wallo.challenge.dto.request.CreateChallengeRequest;
import com.wallo.challenge.dto.request.JoinChallengeRequest;
import com.wallo.challenge.dto.response.CreateChallengeResponse;
import com.wallo.challenge.dto.response.CurrentChallengeResponse;
import com.wallo.challenge.dto.response.JoinChallengeResponse;
import com.wallo.challenge.dto.response.WeeklyRankingResponse;
import com.wallo.challenge.exception.AlreadyJoinedChallengeException;
import com.wallo.challenge.exception.ChallengeNotFoundException;
import com.wallo.challenge.exception.InvalidInviteCodeException;
import com.wallo.challenge.exception.NotChallengeMemberException;
import com.wallo.challenge.exception.SoloFeatureNotAllowedException;
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

    @Test
    void joinsChallengeWhenInviteCodeIsValidAndUserHasNoCurrentChallenge() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.challengeByInviteCode = challenge(10L, "함께 절약", "ABCDEFGH");
        ChallengeService service = new ChallengeServiceImpl(mapper);

        JoinChallengeResponse response = service.joinChallenge(2L, joinRequest("ABCDEFGH"));

        assertEquals(10L, response.getId());
        assertEquals("함께 절약", response.getName());
        assertEquals("ABCDEFGH", response.getInviteCode());
        assertEquals(10L, mapper.currentChallengeId);
    }

    @Test
    void throwsExceptionWhenInviteCodeDoesNotMatchChallenge() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        ChallengeService service = new ChallengeServiceImpl(mapper);

        assertThrows(
                InvalidInviteCodeException.class,
                () -> service.joinChallenge(2L, joinRequest("UNKNOWN1")));
    }

    @Test
    void throwsExceptionWhenJoiningUserAlreadyHasCurrentChallenge() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.currentChallengeId = 10L;
        ChallengeService service = new ChallengeServiceImpl(mapper);

        assertThrows(
                AlreadyJoinedChallengeException.class,
                () -> service.joinChallenge(2L, joinRequest("ABCDEFGH")));
    }

    @Test
    void returnsNotJoinedWhenUserHasNoCurrentChallenge() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        ChallengeService service = new ChallengeServiceImpl(mapper);

        CurrentChallengeResponse response = service.getCurrentChallenge(1L);

        assertFalse(response.isJoined());
        assertNull(response.getId());
    }

    @Test
    void returnsCurrentChallengeWhenUserIsJoined() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.currentChallengeId = 10L;
        mapper.savedChallenge = challenge(10L, "함께 절약", "ABCDEFGH");
        ChallengeService service = new ChallengeServiceImpl(mapper);

        CurrentChallengeResponse response = service.getCurrentChallenge(1L);

        assertTrue(response.isJoined());
        assertEquals(10L, response.getId());
        assertEquals("함께 절약", response.getName());
        assertEquals("ABCDEFGH", response.getInviteCode());
    }

    @Test
    void returnsWeeklyRankingAndLoggedInUsersRanking() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.currentChallengeId = 10L;
        mapper.savedChallenge = challenge(10L, "함께 절약", "GROUP", "ABCDEFGH");
        mapper.weeklyRankings.add(weeklyRanking(10L, 2L, 1, 50000L, 10));
        mapper.weeklyRankings.add(weeklyRanking(10L, 1L, 2, 30000L, 5));
        ChallengeService service = new ChallengeServiceImpl(mapper);

        WeeklyRankingResponse response = service.getWeeklyRanking(1L);

        assertEquals(LocalDate.of(2026, 7, 27), response.getStartDate());
        assertEquals(LocalDate.of(2026, 8, 2), response.getEndDate());
        assertEquals(2, response.getRankings().size());
        assertNotNull(response.getMyRanking());
        assertEquals(1L, response.getMyRanking().getUserId());
        assertEquals(2, response.getMyRanking().getRank());
    }

    @Test
    void throwsExceptionWhenRankingChallengeDoesNotExist() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.currentChallengeId = 10L;
        ChallengeService service = new ChallengeServiceImpl(mapper);

        assertThrows(
                ChallengeNotFoundException.class,
                () -> service.getWeeklyRanking(1L));
    }

    @Test
    void throwsExceptionWhenUserIsNotRankingChallengeMember() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        ChallengeService service = new ChallengeServiceImpl(mapper);

        assertThrows(
                NotChallengeMemberException.class,
                () -> service.getWeeklyRanking(1L));
    }

    @Test
    void throwsExceptionWhenWeeklyRankingIsRequestedForSoloChallenge() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.currentChallengeId = 10L;
        mapper.savedChallenge = challenge(10L, "혼자 절약", "SOLO", "ABCDEFGH");
        ChallengeService service = new ChallengeServiceImpl(mapper);

        assertThrows(
                SoloFeatureNotAllowedException.class,
                () -> service.getWeeklyRanking(1L));
    }

    private CreateChallengeRequest request(String name, String challengeType) {
        CreateChallengeRequest request = new CreateChallengeRequest();
        request.setName(name);
        request.setChallengeType(challengeType);
        return request;
    }

    private JoinChallengeRequest joinRequest(String inviteCode) {
        JoinChallengeRequest request = new JoinChallengeRequest();
        request.setInviteCode(inviteCode);
        return request;
    }

    private Challenge challenge(Long id, String name, String inviteCode) {
        return challenge(id, name, "SAVING", inviteCode);
    }

    private Challenge challenge(Long id, String name, String challengeType, String inviteCode) {
        Challenge challenge = new Challenge();
        challenge.setId(id);
        challenge.setName(name);
        challenge.setChallengeType(challengeType);
        challenge.setInviteCode(inviteCode);
        challenge.setStatus("ACTIVE");
        return challenge;
    }

    private WeeklyRanking weeklyRanking(
            Long challengeId,
            Long userId,
            int rankPosition,
            long savingAmount,
            int likeCount) {
        WeeklyRanking ranking = new WeeklyRanking();
        ranking.setChallengeId(challengeId);
        ranking.setUserId(userId);
        ranking.setWeekStartDate(LocalDate.of(2026, 7, 27));
        ranking.setRankPosition(rankPosition);
        ranking.setNickname("사용자" + userId);
        ranking.setProfileImageUrl("/images/profile.svg");
        ranking.setSavingAmount(savingAmount);
        ranking.setStreakDays(3);
        ranking.setLikeCount(likeCount);
        ranking.setRewardPoint(rankPosition == 1 ? 3000 : 2000);
        return ranking;
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Mapper다. */
    private static class FakeChallengeMapper implements ChallengeMapper {

        private Long currentChallengeId;
        private int updateResult = 1;
        private Challenge savedChallenge;
        private Challenge challengeByInviteCode;
        private final List<WeeklyRanking> weeklyRankings = new ArrayList<>();

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
        public Challenge findChallengeByInviteCode(String inviteCode) {
            return challengeByInviteCode;
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

        @Override
        public List<WeeklyRanking> findWeeklyRankings(Long challengeId) {
            return weeklyRankings;
        }
    }
}
