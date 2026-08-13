package com.wallo.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.chat.dto.AssetAnalysisResultDto;
import com.wallo.chat.dto.AssetAnalysisView;
import com.wallo.chat.mapper.AssetAnalysisResultMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetAnalysisResultService {
    private final AssetAnalysisResultMapper mapper;
    private final ObjectMapper objectMapper;
    private final AssetAnalysisViewAssembler viewAssembler;

    public AssetAnalysisResultService(
            AssetAnalysisResultMapper mapper,
            ObjectMapper objectMapper,
            AssetAnalysisViewAssembler viewAssembler
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.viewAssembler = viewAssembler;
    }

    @Transactional
    public void save(
            long userId,
            long assistantMessageId,
            String requestMessage,
            Map<String, Object> calculatedResult,
            String aiResponse
    ) {
        if (calculatedResult == null || calculatedResult.isEmpty()) {
            return;
        }
        try {
            int inserted = mapper.insert(new AssetAnalysisResultDto.SaveCommand(
                    userId,
                    assistantMessageId,
                    requestMessage,
                    objectMapper.writeValueAsString(calculatedResult),
                    aiResponse
            ));
            if (inserted != 1) {
                throw new IllegalStateException("자산분석 결과를 저장하지 못했습니다.");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "자산분석 계산 결과를 JSON으로 변환하지 못했습니다.", exception);
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, AssetAnalysisView> findByAssistantMessageIds(
            List<Long> assistantMessageIds
    ) {
        if (assistantMessageIds == null || assistantMessageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AssetAnalysisView> result = new LinkedHashMap<>();
        for (AssetAnalysisResultDto.StoredResult stored
                : mapper.findByAssistantMessageIds(assistantMessageIds)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> calculation = objectMapper.readValue(
                        stored.getCalculatedResultJson(), Map.class);
                result.put(
                        stored.getAssistantMessageId(),
                        viewAssembler.assemble(calculation)
                );
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException(
                        "저장된 자산분석 결과를 읽지 못했습니다.", exception);
            }
        }
        return result;
    }
}
