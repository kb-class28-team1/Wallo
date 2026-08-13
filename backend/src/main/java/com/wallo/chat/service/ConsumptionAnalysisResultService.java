package com.wallo.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.chat.dto.ConsumptionAnalysisResultDto;
import com.wallo.chat.dto.ConsumptionAnalysisView;
import com.wallo.chat.mapper.ConsumptionAnalysisResultMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import com.wallo.chat.dto.ConsumptionAnalysisPeriodContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumptionAnalysisResultService {
    private final ConsumptionAnalysisResultMapper mapper;
    private final ObjectMapper objectMapper;
    private final ConsumptionAnalysisViewAssembler viewAssembler;
    private final Clock clock;

    public ConsumptionAnalysisResultService(
            ConsumptionAnalysisResultMapper mapper,
            ObjectMapper objectMapper,
            ConsumptionAnalysisViewAssembler viewAssembler
    ) {
        this(mapper, objectMapper, viewAssembler, Clock.systemDefaultZone());
    }

    ConsumptionAnalysisResultService(
            ConsumptionAnalysisResultMapper mapper,
            ObjectMapper objectMapper,
            ConsumptionAnalysisViewAssembler viewAssembler,
            Clock clock
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.viewAssembler = viewAssembler;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<CachedAnalysis> findReusable(long userId) {
        ConsumptionAnalysisResultDto.RecentResult stored = mapper.findLatestSince(
                userId, LocalDateTime.now(clock).minusDays(7));
        if (stored == null) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> calculation = objectMapper.readValue(
                    stored.getCalculatedResultJson(), Map.class);
            return Optional.of(new CachedAnalysis(
                    stored.getAnalysisResultId(), calculation, stored.getAiResponse()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 소비분석 결과를 읽지 못했습니다.", exception);
        }
    }

    public record CachedAnalysis(
            long analysisResultId,
            Map<String, Object> calculatedResult,
            String aiResponse
    ) {}

    @Transactional
    public boolean save(
            long userId,
            long assistantMessageId,
            String requestMessage,
            Map<String, Object> calculatedResult,
            String aiResponse
    ) {
        if (calculatedResult == null) {
            return false;
        }
        boolean firstAnalysis = mapper.countByUserId(userId) == 0;
        try {
            int inserted = mapper.insert(new ConsumptionAnalysisResultDto.SaveCommand(
                    userId,
                    assistantMessageId,
                    requestMessage,
                    objectMapper.writeValueAsString(calculatedResult),
                    aiResponse
            ));
            if (inserted != 1) {
                throw new IllegalStateException("소비분석 결과를 저장하지 못했습니다.");
            }
            return firstAnalysis;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "소비분석 계산 결과를 JSON으로 변환하지 못했습니다.", exception);
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, ConsumptionAnalysisView> findByAssistantMessageIds(
            List<Long> assistantMessageIds
    ) {
        if (assistantMessageIds == null || assistantMessageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ConsumptionAnalysisView> result = new LinkedHashMap<>();
        for (ConsumptionAnalysisResultDto.StoredResult stored
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
                        "저장된 소비분석 결과를 읽지 못했습니다.", exception);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ConsumptionAnalysisPeriodContext findLatestPeriod(
            List<Long> assistantMessageIds
    ) {
        Map<Long, ConsumptionAnalysisView> analyses =
                findByAssistantMessageIds(assistantMessageIds);
        if (assistantMessageIds == null) {
            return null;
        }
        for (int index = assistantMessageIds.size() - 1; index >= 0; index--) {
            ConsumptionAnalysisView analysis = analyses.get(assistantMessageIds.get(index));
            if (analysis != null && analysis.period() != null) {
                return ConsumptionAnalysisPeriodContext.from(analysis.period());
            }
        }
        return null;
    }
}
