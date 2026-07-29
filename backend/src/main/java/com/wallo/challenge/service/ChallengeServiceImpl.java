package com.wallo.challenge.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wallo.challenge.domain.Challenge;
import com.wallo.challenge.dto.request.CreateChallengeRequest;
import com.wallo.challenge.dto.response.CreateChallengeResponse;
import com.wallo.challenge.exception.AlreadyJoinedChallengeException;
import com.wallo.challenge.mapper.ChallengeMapper;

@Service
public class ChallengeServiceImpl implements ChallengeService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int MAX_INVITE_CODE_GENERATION_ATTEMPTS = 10;

    private final ChallengeMapper challengeMapper;
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
        }

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
