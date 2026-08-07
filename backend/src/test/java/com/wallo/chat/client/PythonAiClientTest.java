package com.wallo.chat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import com.wallo.goal.dto.GoalInterviewDto;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PythonAiClientTest {

    @Test
    void springCreatesClientWithConfiguredObjectMapper() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, this::objectMapper);
            context.registerBean(PythonAiClient.class);
            context.refresh();

            assertNotNull(context.getBean(PythonAiClient.class));
        }
    }

    @Test
    void serializesGoalTargetDateAsIsoString() {
        ObjectMapper objectMapper = objectMapper();
        RestTemplate restTemplate = PythonAiClient.createRestTemplate(objectMapper);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:8000/api/chat"))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request)
                            .getBodyAsString(StandardCharsets.UTF_8);
                    assertTrue(body.contains("\"targetDate\":\"2027-02-06\""));
                    assertTrue(body.contains("\"goalAlreadyExists\":true"));
                    assertFalse(body.contains("\"targetDate\":[2027,2,6]"));
                })
                .andRespond(withSuccess("{\"answer\":\"다음 질문\"}", MediaType.APPLICATION_JSON));
        PythonAiClient client = new PythonAiClient(
                restTemplate,
                "http://127.0.0.1:8000"
        );

        ChatResponse response = client.chat(
                new ChatRequest("11월까지 프랑스 여행 자금을 모으고 싶어")
                        .withGoalDraft(draft())
                        .withGoalAlreadyExists(true)
        );

        assertEquals("다음 질문", response.answer());
        server.verify();
    }

    @Test
    void convertsAiServer429ToRateLimitException() {
        ObjectMapper objectMapper = objectMapper();
        RestTemplate restTemplate = PythonAiClient.createRestTemplate(objectMapper);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:8000/api/chat"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"현재 AI 사용량 한도에 도달했습니다.\"}"));
        PythonAiClient client = new PythonAiClient(
                restTemplate,
                "http://127.0.0.1:8000"
        );

        AiRateLimitException exception = assertThrows(
                AiRateLimitException.class,
                () -> client.chat(new ChatRequest("비상금 목표를 만들고 싶어"))
        );

        assertEquals(
                "현재 AI 사용량 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.",
                exception.getMessage()
        );
        server.verify();
    }

    private GoalInterviewDto.Draft draft() {
        return new GoalInterviewDto.Draft(
                "DETAILING",
                "프랑스 여행 자금",
                "TRAVEL",
                12_000_000L,
                LocalDate.of(2027, 2, 6),
                null,
                null,
                null,
                null,
                List.of("motivation"),
                List.of(),
                false
        );
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
