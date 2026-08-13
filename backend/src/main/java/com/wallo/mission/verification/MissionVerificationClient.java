package com.wallo.mission.verification;

import com.wallo.mission.dto.MissionVerificationDto;
public interface MissionVerificationClient {
    MissionVerificationDto.AiResult verify(
            byte[] media, String contentType, MissionVerificationDto.MissionSpec mission);
}
