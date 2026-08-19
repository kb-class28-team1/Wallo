package com.wallo.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.chat.dto.ProductRecommendationResultDto;
import com.wallo.chat.mapper.ProductRecommendationResultMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProductRecommendationResultServiceTest {

    @Mock
    private ProductRecommendationResultMapper mapper;

    private ProductRecommendationResultService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProductRecommendationResultService(mapper, new ObjectMapper());
    }

    @Test
    void savesNonEmptyRecommendationAsJson() throws Exception {
        when(mapper.insert(any(ProductRecommendationResultDto.SaveCommand.class))).thenReturn(1);

        service.save(
                7L,
                31L,
                "Recommend a 12-month deposit",
                Map.of(
                        "productType", "deposit",
                        "products", List.of(Map.of("productName", "Safe Deposit"))
                ),
                "I recommend this product."
        );

        var commandCaptor = org.mockito.ArgumentCaptor
                .forClass(ProductRecommendationResultDto.SaveCommand.class);
        verify(mapper).insert(commandCaptor.capture());
        ProductRecommendationResultDto.SaveCommand command = commandCaptor.getValue();
        assertEquals(7L, command.getUserId());
        assertEquals(31L, command.getAssistantMessageId());
        assertEquals("Recommend a 12-month deposit", command.getRequestMessage());
        var json = new ObjectMapper().readTree(command.getRecommendationResultJson());
        assertEquals("deposit", json.get("productType").asText());
        assertEquals("Safe Deposit", json.get("products").get(0).get("productName").asText());
        assertEquals("I recommend this product.", command.getAiResponse());
    }

    @Test
    void skipsEmptyRecommendation() {
        service.save(7L, 31L, "Request", Map.of(), "Answer");

        verifyNoInteractions(mapper);
    }

    @Test
    void returnsLatestRecommendationWithParsedJson() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 8, 19, 10, 1);
        when(mapper.findLatestByUserId(7L)).thenReturn(
                new ProductRecommendationResultDto.LatestStoredResult(
                        31L,
                        "Recommend a 12-month deposit",
                        "{\"productType\":\"deposit\",\"products\":[{\"productName\":\"Safe Deposit\"}]}",
                        "I recommend this product.",
                        generatedAt
                )
        );

        ProductRecommendationResultDto.LatestResponse response =
                service.findLatestByUserId(7L);

        assertNotNull(response);
        assertEquals(31L, response.getAssistantMessageId());
        assertEquals("Recommend a 12-month deposit", response.getRequestMessage());
        assertEquals(
                "Safe Deposit",
                ((Map<?, ?>) ((List<?>) response.getProductRecommendation().get("products"))
                        .get(0)).get("productName")
        );
        assertEquals("I recommend this product.", response.getAiResponse());
        assertEquals(generatedAt, response.getGeneratedAt());
        verify(mapper).findLatestByUserId(7L);
    }

    @Test
    void returnsNullWhenUserHasNoRecommendation() {
        when(mapper.findLatestByUserId(7L)).thenReturn(null);

        assertNull(service.findLatestByUserId(7L));
    }

    @Test
    void rejectsMalformedStoredJson() {
        when(mapper.findLatestByUserId(7L)).thenReturn(
                new ProductRecommendationResultDto.LatestStoredResult(
                        31L,
                        "Request",
                        "not-json",
                        "Answer",
                        LocalDateTime.of(2026, 8, 19, 10, 1)
                )
        );

        assertThrows(IllegalStateException.class, () -> service.findLatestByUserId(7L));
    }
}
