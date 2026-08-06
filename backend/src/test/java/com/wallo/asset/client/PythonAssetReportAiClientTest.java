package com.wallo.asset.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.chat.client.AiServerException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class PythonAssetReportAiClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final PythonAssetReportAiClient client = new PythonAssetReportAiClient(
            restTemplate,
            "http://localhost:8000/"
    );

    @Test
    void callsAssetReportInsightEndpointAndReturnsResponse() {
        AssetReportAiDto.Request request = new AssetReportAiDto.Request(
                "CAFE",
                "카페",
                300_000L,
                200_000L
        );
        AssetReportAiDto.Response expected = new AssetReportAiDto.Response(
                "카페 지출이 가장 많아요",
                "이번 달은 카페 지출이 가장 많아요. 이용 횟수를 조금 줄여보는 것도 좋아요."
        );
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/asset-reports/insights/generate")),
                eq(request),
                eq(AssetReportAiDto.Response.class)
        )).thenReturn(ResponseEntity.ok(expected));

        AssetReportAiDto.Response actual = client.generate(request);

        assertEquals(expected, actual);
        verify(restTemplate).postForEntity(
                eq(URI.create("http://localhost:8000/api/asset-reports/insights/generate")),
                eq(request),
                eq(AssetReportAiDto.Response.class)
        );
    }

    @Test
    void rejectsEmptyResponse() {
        AssetReportAiDto.Request request = new AssetReportAiDto.Request(
                "FOOD",
                "식비",
                100_000L,
                80_000L
        );
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/asset-reports/insights/generate")),
                eq(request),
                eq(AssetReportAiDto.Response.class)
        )).thenReturn(ResponseEntity.<AssetReportAiDto.Response>ok(null));

        assertThrows(AiServerException.class, () -> client.generate(request));
    }

    @Test
    void rejectsBlankResponseText() {
        AssetReportAiDto.Request request = new AssetReportAiDto.Request(
                "FOOD",
                "식비",
                100_000L,
                80_000L
        );
        AssetReportAiDto.Response invalid = new AssetReportAiDto.Response(" ", "내용");
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/asset-reports/insights/generate")),
                eq(request),
                eq(AssetReportAiDto.Response.class)
        )).thenReturn(ResponseEntity.ok(invalid));

        assertThrows(AiServerException.class, () -> client.generate(request));
    }

    @Test
    void rejectsResponseThatExceedsTextContract() {
        AssetReportAiDto.Request request = new AssetReportAiDto.Request(
                "FOOD",
                "식비",
                100_000L,
                80_000L
        );
        AssetReportAiDto.Response invalid = new AssetReportAiDto.Response(
                "1234567890123456",
                "내용"
        );
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/asset-reports/insights/generate")),
                eq(request),
                eq(AssetReportAiDto.Response.class)
        )).thenReturn(ResponseEntity.ok(invalid));

        assertThrows(AiServerException.class, () -> client.generate(request));
    }

    @Test
    void rejectsMarkdownWrappedResponseText() {
        AssetReportAiDto.Request request = new AssetReportAiDto.Request(
                "FOOD",
                "식비",
                100_000L,
                80_000L
        );
        AssetReportAiDto.Response invalid = new AssetReportAiDto.Response(
                "```json",
                "내용"
        );
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/asset-reports/insights/generate")),
                eq(request),
                eq(AssetReportAiDto.Response.class)
        )).thenReturn(ResponseEntity.ok(invalid));

        assertThrows(AiServerException.class, () -> client.generate(request));
    }

    @Test
    void rejectsJsonWrappedResponseText() {
        AssetReportAiDto.Request request = new AssetReportAiDto.Request(
                "FOOD",
                "식비",
                100_000L,
                80_000L
        );
        AssetReportAiDto.Response invalid = new AssetReportAiDto.Response(
                "제목",
                "{\"report\":\"본문\"}"
        );
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/asset-reports/insights/generate")),
                eq(request),
                eq(AssetReportAiDto.Response.class)
        )).thenReturn(ResponseEntity.ok(invalid));

        assertThrows(AiServerException.class, () -> client.generate(request));
    }

    @Test
    void trimsValidResponseTextBeforeReturningIt() {
        AssetReportAiDto.Request request = new AssetReportAiDto.Request(
                "FOOD",
                "식비",
                100_000L,
                80_000L
        );
        AssetReportAiDto.Response response = new AssetReportAiDto.Response(
                " 제목 ",
                " 본문 "
        );
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/asset-reports/insights/generate")),
                eq(request),
                eq(AssetReportAiDto.Response.class)
        )).thenReturn(ResponseEntity.ok(response));

        assertEquals(
                new AssetReportAiDto.Response("제목", "본문"),
                client.generate(request)
        );
    }

    @Test
    void convertsConnectionFailureToAiServerException() {
        AssetReportAiDto.Request request = new AssetReportAiDto.Request(
                "SHOPPING",
                "쇼핑",
                100_000L,
                80_000L
        );
        when(restTemplate.postForEntity(
                eq(URI.create("http://localhost:8000/api/asset-reports/insights/generate")),
                eq(request),
                eq(AssetReportAiDto.Response.class)
        )).thenThrow(new ResourceAccessException("connection refused"));

        assertThrows(AiServerException.class, () -> client.generate(request));
    }
}
