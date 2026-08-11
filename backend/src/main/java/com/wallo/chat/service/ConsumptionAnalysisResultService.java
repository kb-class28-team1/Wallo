package com.wallo.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.chat.dto.ConsumptionAnalysisResultDto;
import com.wallo.chat.mapper.ConsumptionAnalysisResultMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumptionAnalysisResultService {
    private final ConsumptionAnalysisResultMapper mapper;
    private final ObjectMapper objectMapper;

    public ConsumptionAnalysisResultService(
            ConsumptionAnalysisResultMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(
            long userId,
            String requestMessage,
            Map<String, Object> calculatedResult,
            String aiResponse
    ) {
        if (calculatedResult == null) {
            return;
        }
        try {
            int inserted = mapper.insert(new ConsumptionAnalysisResultDto.SaveCommand(
                    userId,
                    requestMessage,
                    objectMapper.writeValueAsString(calculatedResult),
                    aiResponse
            ));
            if (inserted != 1) {
                throw new IllegalStateException("소비분석 결과를 저장하지 못했습니다.");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "소비분석 계산 결과를 JSON으로 변환하지 못했습니다.", exception);
        }
    }
}
