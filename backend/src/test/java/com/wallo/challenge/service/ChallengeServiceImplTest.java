package com.wallo.challenge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.wallo.challenge.domain.Challenge;
import com.wallo.challenge.domain.MonthlySaving;
import com.wallo.challenge.domain.MyChallengeSummary;
import com.wallo.challenge.domain.TopLikedFeed;
import com.wallo.challenge.domain.WeeklyRanking;
import com.wallo.challenge.dto.request.CreateChallengeRequest;
import com.wallo.challenge.dto.request.JoinChallengeRequest;
import com.wallo.challenge.dto.response.CreateChallengeResponse;
import com.wallo.challenge.dto.response.CurrentChallengeResponse;
import com.wallo.challenge.dto.response.JoinChallengeResponse;
import com.wallo.challenge.dto.response.MyChallengeDashboardResponse;
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

        CreateChallengeResponse response = service.createChallenge(1L, request("7월 절약"));

        assertEquals(1L, response.getId());
        assertEquals("7월 절약", response.getName());
        assertEquals("GROUP", response.getChallengeType());
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
                () -> service.createChallenge(1L, request("새 챌린지")));
    }

    @Test
    void throwsExceptionWhenAnotherRequestConnectsOwnerFirst() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.updateResult = 0;
        ChallengeService service = new ChallengeServiceImpl(mapper);

        assertThrows(
                AlreadyJoinedChallengeException.class,
                () -> service.createChallenge(1L, request("새 챌린지")));
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

    @Test
    void returnsMyChallengeDashboardForCurrentUser() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.myChallengeSummary = myChallengeSummary(1L, 10L);
        mapper.monthlySavings.add(monthlySaving("2026-07", 45000L));
        mapper.topLikedFeeds.add(topLikedFeed(100L, 10L, 28));
        ChallengeService service = new ChallengeServiceImpl(mapper);

        MyChallengeDashboardResponse response = service.getMyChallengeDashboard(1L);

        assertEquals(1L, response.getUserId());
        assertEquals("김혜진", response.getNickname());
        assertEquals(10L, response.getCurrentChallengeId());
        assertEquals(1285600L, response.getTotalSavingAmount());
        assertEquals(new BigDecimal("12.0"), response.getSavingChangeRate());
        assertEquals(1, response.getMonthlySavings().size());
        assertEquals("2026-07", response.getMonthlySavings().get(0).getMonth());
        assertEquals(1, response.getTopLikedFeeds().size());
        assertEquals(28, response.getTopLikedFeeds().get(0).getLikeCount());
    }

    @Test
    void throwsExceptionWhenDashboardUserHasNoCurrentChallenge() {
        FakeChallengeMapper mapper = new FakeChallengeMapper();
        mapper.myChallengeSummary = myChallengeSummary(1L, null);
        ChallengeService service = new ChallengeServiceImpl(mapper);

        assertThrows(
                NotChallengeMemberException.class,
                () -> service.getMyChallengeDashboard(1L));
    }

    private CreateChallengeRequest request(String name) {
        CreateChallengeRequest request = new CreateChallengeRequest();
        request.setName(name);
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

    private MyChallengeSummary myChallengeSummary(Long userId, Long challengeId) {
        MyChallengeSummary summary = new MyChallengeSummary();
        summary.setUserId(userId);
        summary.setNickname("김혜진");
        summary.setProfileImageUrl("/images/profile.svg");
        summary.setJoinedAt(LocalDate.of(2025, 1, 15));
        summary.setStreakDays(12);
        summary.setCurrentChallengeId(challengeId);
        summary.setCurrentChallengeName(challengeId == null ? null : "함께 절약");
        summary.setTotalSavingAmount(1285600L);
        summary.setCurrentMonthSavingAmount(186500L);
        summary.setPreviousMonthSavingAmount(166500L);
        summary.setSavingChangeRate(new BigDecimal("12.0"));
        summary.setVerificationCount(48);
        summary.setAverageSavingAmount(26783L);
        summary.setPostCount(32);
        summary.setReceivedLikeCount(236);
        summary.setCommentCount(58);
        return summary;
    }

    private MonthlySaving monthlySaving(String month, Long savingAmount) {
        MonthlySaving monthlySaving = new MonthlySaving();
        monthlySaving.setMonth(month);
        monthlySaving.setSavingAmount(savingAmount);
        return monthlySaving;
    }

    private TopLikedFeed topLikedFeed(Long feedId, Long challengeId, Integer likeCount) {
        TopLikedFeed feed = new TopLikedFeed();
        feed.setFeedId(feedId);
        feed.setChallengeId(challengeId);
        feed.setCaption("오늘의 커피 절약");
        feed.setThumbnailUrl("/images/feed-thumbnail.jpg");
        feed.setMediaUrl("/images/feed.jpg");
        feed.setLikeCount(likeCount);
        feed.setCreatedAt(LocalDateTime.of(2026, 7, 31, 9, 30));
        return feed;
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Mapper다. */
    private static class FakeChallengeMapper implements ChallengeMapper {

        private Long currentChallengeId;
        private int updateResult = 1;
        private Challenge savedChallenge;
        private Challenge challengeByInviteCode;
        private final List<WeeklyRanking> weeklyRankings = new ArrayList<>();
        private MyChallengeSummary myChallengeSummary;
        private final List<MonthlySaving> monthlySavings = new ArrayList<>();
        private final List<TopLikedFeed> topLikedFeeds = new ArrayList<>();

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

        @Override
        public MyChallengeSummary findMyChallengeSummary(Long userId) {
            return myChallengeSummary;
        }

        @Override
        public List<MonthlySaving> findMonthlySavings(Long userId) {
            return monthlySavings;
        }

        @Override
        public List<TopLikedFeed> findTopLikedFeeds(Long userId) {
            return topLikedFeeds;
        }
    }
}
