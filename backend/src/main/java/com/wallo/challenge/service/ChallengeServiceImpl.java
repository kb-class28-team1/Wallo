package com.wallo.challenge.service;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import com.wallo.challenge.dto.response.WeeklyRankingItemResponse;
import com.wallo.challenge.dto.response.WeeklyRankingResponse;
import com.wallo.challenge.exception.AlreadyJoinedChallengeException;
import com.wallo.challenge.exception.ChallengeNotFoundException;
import com.wallo.challenge.exception.InvalidInviteCodeException;
import com.wallo.challenge.exception.NotChallengeMemberException;
import com.wallo.challenge.exception.SoloFeatureNotAllowedException;
import com.wallo.challenge.mapper.ChallengeMapper;

@Service
public class ChallengeServiceImpl implements ChallengeService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String GROUP_CHALLENGE_TYPE = "GROUP";
    private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int MAX_INVITE_CODE_GENERATION_ATTEMPTS = 10;

    private final ChallengeMapper challengeMapper;
    // secureRandom: 일반 Random 보다 더 예측하기 어려운 난수생성 메소드
    private final SecureRandom secureRandom = new SecureRandom();

    public ChallengeServiceImpl(ChallengeMapper challengeMapper) {
        this.challengeMapper = challengeMapper;
    }

    /**
     * 챌린지 저장과 사용자의 current_challenge_id 갱신은 함께 성공하거나 함께 취소돼야 한다.
     */
    @Override
    @Transactional
    public CreateChallengeResponse createChallenge(Long ownerId, CreateChallengeRequest request) {
        if (challengeMapper.findCurrentChallengeIdByUserId(ownerId) != null) {
            throw new AlreadyJoinedChallengeException();
        } // -> 이미 참여 중인 챌린지가 있다면 새로 못 만들게 막는 것

        Challenge challenge = new Challenge();
        challenge.setOwnerId(ownerId);
        challenge.setName(request.getName());
        challenge.setChallengeType(request.getChallengeType());
        challenge.setInviteCode(generateUniqueInviteCode());
        challenge.setStatus(ACTIVE_STATUS);
        challenge.setCreatedAt(LocalDateTime.now());

        int insertedRows = challengeMapper.insertChallenge(challenge);
        if (insertedRows != 1) {
            throw new IllegalStateException("챌린지 저장에 실패했습니다.");
        }

        // 동시 요청에도 이미 참여한 사용자는 갱신되지 않도록 Mapper SQL에서 조건을 건다.
        int updatedRows = challengeMapper.updateCurrentChallengeId(ownerId, challenge.getId());
        if (updatedRows != 1) {
            throw new AlreadyJoinedChallengeException();
        }

        Challenge createdChallenge = challengeMapper.findChallengeById(challenge.getId());
        if (createdChallenge == null) {
            throw new IllegalStateException("생성된 챌린지를 조회할 수 없습니다.");
        }

        return CreateChallengeResponse.from(createdChallenge);
    }

    /**
     * 초대 코드가 가리키는 챌린지에 사용자를 참여시킨다.
     * 현재 챌린지 연결 갱신이 실패하면 트랜잭션 전체가 취소된다.
     */
    @Override
    @Transactional
    public JoinChallengeResponse joinChallenge(Long userId, JoinChallengeRequest request) {
        if (challengeMapper.findCurrentChallengeIdByUserId(userId) != null) {
            throw new AlreadyJoinedChallengeException();
        }

        String inviteCode = request == null ? null : request.getInviteCode();
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new InvalidInviteCodeException();
        }

        Challenge challenge = challengeMapper.findChallengeByInviteCode(inviteCode.trim());
        if (challenge == null) {
            throw new InvalidInviteCodeException();
        }

        int updatedRows = challengeMapper.updateCurrentChallengeId(userId, challenge.getId());
        if (updatedRows != 1) {
            throw new AlreadyJoinedChallengeException();
        }

        return JoinChallengeResponse.from(challenge);
    }

    /** 참여 중이 아니면 오류 대신 joined가 false인 정상 응답을 반환한다. */
    @Override
    public CurrentChallengeResponse getCurrentChallenge(Long userId) {
        Long challengeId = challengeMapper.findCurrentChallengeIdByUserId(userId);
        if (challengeId == null) {
            return CurrentChallengeResponse.notJoined();
        }

        Challenge challenge = challengeMapper.findChallengeById(challengeId);
        if (challenge == null) {
            throw new IllegalStateException("현재 참여 챌린지를 조회할 수 없습니다.");
        }

        return CurrentChallengeResponse.joined(challenge);
    }

    /**
     * 주간 랭킹을 조회하기 전에 챌린지 존재 여부, 사용자 참여 여부와 GROUP 유형을 차례로 확인함.
     * 검증을 통과하면 VIEW 조회 결과를 화면 응답 DTO로 변환하고 로그인 사용자의 순위도 함께 찾음.
     */
    @Override
    @Transactional(readOnly = true)
    public WeeklyRankingResponse getWeeklyRanking(Long userId) {
        // 로그인 사용자의 current_challenge_id를 기준으로 조회 대상을 결정함.
        Long challengeId = challengeMapper.findCurrentChallengeIdByUserId(userId);
        if (challengeId == null) {
            throw new NotChallengeMemberException();
        }

        Challenge challenge = challengeMapper.findChallengeById(challengeId);
        if (challenge == null) {
            throw new ChallengeNotFoundException();
        }

        // 주간 랭킹은 여러 사용자가 참여하는 GROUP 챌린지에서만 제공함.
        if (!GROUP_CHALLENGE_TYPE.equals(challenge.getChallengeType())) {
            throw new SoloFeatureNotAllowedException();
        }

        List<WeeklyRanking> weeklyRankings = challengeMapper.findWeeklyRankings(challengeId);
        List<WeeklyRankingItemResponse> rankingResponses = weeklyRankings.stream()
                .map(WeeklyRankingItemResponse::from)
                .collect(Collectors.toList());

        // 전체 랭킹 중 로그인 사용자와 userId가 같은 항목을 내 순위로 분리함.
        WeeklyRankingItemResponse myRanking = weeklyRankings.stream()
                .filter(ranking -> userId.equals(ranking.getUserId()))
                .findFirst()
                .map(WeeklyRankingItemResponse::from)
                .orElse(null);

        // VIEW 결과가 비어 있어도 현재 주의 월요일부터 일요일까지를 응답하도록 처리함.
        LocalDate startDate = weeklyRankings.isEmpty()
                ? LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : weeklyRankings.get(0).getWeekStartDate();

        return WeeklyRankingResponse.of(
                startDate,
                startDate.plusDays(6),
                rankingResponses,
                myRanking);
    }

    /**
     * 내 챌린지 화면에 필요한 요약, 최근 6개월 절약 금액과 인기 피드를 조회함.
     * 현재 챌린지가 없는 사용자는 대시보드에 접근할 수 없도록 처리함.
     */
    @Override
    @Transactional(readOnly = true)
    public MyChallengeDashboardResponse getMyChallengeDashboard(Long userId) {
        MyChallengeSummary summary = challengeMapper.findMyChallengeSummary(userId);

        if (summary == null || summary.getCurrentChallengeId() == null) {
            throw new NotChallengeMemberException();
        }

        List<MonthlySaving> monthlySavings = challengeMapper.findMonthlySavings(userId);
        List<TopLikedFeed> topLikedFeeds = challengeMapper.findTopLikedFeeds(userId);

        return MyChallengeDashboardResponse.of(summary, monthlySavings, topLikedFeeds);
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_INVITE_CODE_GENERATION_ATTEMPTS; attempt++) {
            String inviteCode = generateInviteCode();
            if (challengeMapper.countByInviteCode(inviteCode) == 0) {
                return inviteCode;
            }
        }

        throw new IllegalStateException("고유한 초대 코드를 생성하지 못했습니다.");
    }

    private String generateInviteCode() {
        StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
        for (int index = 0; index < INVITE_CODE_LENGTH; index++) {
            int randomIndex = secureRandom.nextInt(INVITE_CODE_CHARACTERS.length());
            builder.append(INVITE_CODE_CHARACTERS.charAt(randomIndex));
        }
        return builder.toString();
    }
}
