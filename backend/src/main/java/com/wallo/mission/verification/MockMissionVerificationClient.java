package com.wallo.mission.verification;

import com.wallo.mission.dto.MissionVerificationDto;
public class MockMissionVerificationClient implements MissionVerificationClient {
    @Override
    public MissionVerificationDto.AiResult verify(
            byte[] media, String contentType, MissionVerificationDto.MissionSpec mission) {
        return new MissionVerificationDto.AiResult(
                "REVIEW", 0.0, "AI 미션 판정 기능이 비활성화되어 있습니다.", "mock");
    }
}
