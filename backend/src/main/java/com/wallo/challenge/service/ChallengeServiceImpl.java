package com.wallo.challenge.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wallo.challenge.domain.Challenge;
import com.wallo.challenge.dto.request.CreateChallengeRequest;
import com.wallo.challenge.dto.request.JoinChallengeRequest;
import com.wallo.challenge.dto.response.CreateChallengeResponse;
import com.wallo.challenge.dto.response.CurrentChallengeResponse;
import com.wallo.challenge.dto.response.JoinChallengeResponse;
import com.wallo.challenge.exception.AlreadyJoinedChallengeException;
import com.wallo.challenge.exception.InvalidInviteCodeException;
import com.wallo.challenge.mapper.ChallengeMapper;

@Service
public class ChallengeServiceImpl implements ChallengeService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String DEFAULT_CHALLENGE_TYPE = "GROUP";
    private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 5;
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
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("챌린지 이름을 입력해 주세요.");
        }
        String challengeName = request.getName().trim();
        if (challengeName.length() > 20) {
            throw new IllegalArgumentException("챌린지 이름은 20자 이하로 입력해 주세요.");
        }
        challenge.setName(challengeName);
        // 유형은 사용자에게 받지 않고 모든 신규 챌린지를 함께하는 그룹형으로 생성한다.
        challenge.setChallengeType(DEFAULT_CHALLENGE_TYPE);
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

        String inviteCode = normalizeInviteCode(request == null ? null : request.getInviteCode());
        if (inviteCode == null || inviteCode.isEmpty()) {
            throw new InvalidInviteCodeException();
        }

        Challenge challenge = challengeMapper.findChallengeByInviteCode(inviteCode);
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

    private String normalizeInviteCode(String inviteCode) {
        return inviteCode == null ? null : inviteCode.trim().toUpperCase(Locale.ROOT);
    }
}
