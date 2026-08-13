package com.wallo.mission.client;

import com.wallo.mission.dto.MissionGenerationDto;

public interface MissionAiClient {
    MissionGenerationDto.Response generate(MissionGenerationDto.Request request);
}

