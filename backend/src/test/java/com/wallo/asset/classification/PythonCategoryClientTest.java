package com.wallo.asset.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.chat.client.AiServerException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class PythonCategoryClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final PythonCategoryClient client = new PythonCategoryClient(
            restTemplate,
            "http://localhost:8000/"
    );

    @Test
    void callsCategoryEndpointAndReturnsClassification() {
        CategoryClassificationDto.Request request = new CategoryClassificationDto.Request(
                "알 수 없는 생활용품점",
                "기타",
                12_000L
        );
        CategoryClassificationDto.Response expected = new CategoryClassificationDto.Response(
                "LIVING",
                new BigDecimal("0.8600")
        );
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/category/classify")),
                eq(request),
                eq(CategoryClassificationDto.Response.class)
        )).thenReturn(ResponseEntity.ok(expected));

        CategoryClassificationDto.Response actual = client.classify(request);

        assertEquals(expected, actual);
        verify(restTemplate).postForEntity(
                eq(URI.create("http://localhost:8000/api/category/classify")),
                eq(request),
                eq(CategoryClassificationDto.Response.class)
        );
    }

    @Test
    void rejectsEmptyResponse() {
        CategoryClassificationDto.Request request = new CategoryClassificationDto.Request(
                "분류 불가 상점",
                null,
                1_000L
        );
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/category/classify")),
                eq(request),
                eq(CategoryClassificationDto.Response.class)
        )).thenReturn(ResponseEntity.ok(null));

        assertThrows(AiServerException.class, () -> client.classify(request));
    }

    @Test
    void callsBatchCategoryEndpointAndReturnsClassificationsInOrder() {
        List<CategoryClassificationDto.Request> requests = List.of(
                new CategoryClassificationDto.Request("unknown one", null, 12_000L),
                new CategoryClassificationDto.Request("unknown two", "restaurant", 18_000L)
        );
        CategoryClassificationDto.BatchResponse expected = new CategoryClassificationDto.BatchResponse(List.of(
                new CategoryClassificationDto.Response("LIVING", new BigDecimal("0.8600")),
                new CategoryClassificationDto.Response("FOOD", new BigDecimal("0.9100"))
        ));
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/category/classify/batch")),
                eq(new CategoryClassificationDto.BatchRequest(requests)),
                eq(CategoryClassificationDto.BatchResponse.class)
        )).thenReturn(ResponseEntity.ok(expected));

        List<CategoryClassificationDto.Response> actual = client.classifyBatch(requests);

        assertEquals(expected.results(), actual);
        verify(restTemplate).postForEntity(
                eq(URI.create("http://localhost:8000/api/category/classify/batch")),
                eq(new CategoryClassificationDto.BatchRequest(requests)),
                eq(CategoryClassificationDto.BatchResponse.class)
        );
    }

    @Test
    void convertsConnectionFailureToAiServerException() {
        CategoryClassificationDto.Request request = new CategoryClassificationDto.Request(
                "상점",
                "기타",
                1_000L
        );
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/category/classify")),
                eq(request),
                eq(CategoryClassificationDto.Response.class)
        )).thenThrow(new ResourceAccessException("connection refused"));

        assertThrows(AiServerException.class, () -> client.classify(request));
    }
}
