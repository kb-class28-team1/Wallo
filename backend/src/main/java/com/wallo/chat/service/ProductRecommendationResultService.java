package com.wallo.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.chat.dto.ProductRecommendationResultDto;
import com.wallo.chat.mapper.ProductRecommendationResultMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductRecommendationResultService {
    private final ProductRecommendationResultMapper mapper;
    private final ObjectMapper objectMapper;

    public ProductRecommendationResultService(
            ProductRecommendationResultMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(
            long userId,
            long assistantMessageId,
            String requestMessage,
            Map<String, Object> recommendationResult,
            String aiResponse
    ) {
        if (recommendationResult == null || recommendationResult.isEmpty()) {
            return;
        }
        try {
            int inserted = mapper.insert(new ProductRecommendationResultDto.SaveCommand(
                    userId,
                    assistantMessageId,
                    requestMessage,
                    objectMapper.writeValueAsString(recommendationResult),
                    aiResponse
            ));
            if (inserted != 1) {
                throw new IllegalStateException("상품 추천 결과를 저장하지 못했습니다.");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "상품 추천 결과를 JSON으로 변환하지 못했습니다.", exception);
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, Map<String, Object>> findByAssistantMessageIds(
            List<Long> assistantMessageIds
    ) {
        if (assistantMessageIds == null || assistantMessageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (ProductRecommendationResultDto.StoredResult stored
                : mapper.findByAssistantMessageIds(assistantMessageIds)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> recommendation = objectMapper.readValue(
                        stored.getRecommendationResultJson(), Map.class);
                result.put(stored.getAssistantMessageId(), recommendation);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException(
                        "저장된 상품 추천 결과를 읽지 못했습니다.", exception);
            }
        }
        return result;
    }
}
