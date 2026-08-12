package com.wallo.feed.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class SerpApiGatheredQuantityClientTest {

    @Test
    void extractsAverageQuantityFromSearchResultRange() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> {
                    String query = URLDecoder.decode(
                            request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertTrue(query.contains("engine=google"));
                    assertTrue(query.contains("고구마"));
                    assertTrue(query.contains("평균"));
                    assertTrue(query.contains("1kg당"));
                })
                .andRespond(withSuccess("""
                        {
                          "organic_results": [
                            {
                              "title": "국산 고구마 1kg 4~6개",
                              "snippet": "고구마 1kg 평균 4~6개"
                            },
                            {
                              "title": "고구마 1kg 5개",
                              "snippet": ""
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GatheredQuantityClient client = new SerpApiGatheredQuantityClient(
                restTemplate, "https://serpapi.test/search.json", "test-key", 20);

        OptionalInt result = client.findAveragePackageQuantity("고구마", "판매 단위");

        assertTrue(result.isPresent());
        assertEquals(5, result.getAsInt());
        server.verify();
    }

    @Test
    void extractsQuantityWrittenAsPerKilogram() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertTrue(URLDecoder.decode(
                        request.getURI().getRawQuery(), StandardCharsets.UTF_8)
                .contains("풋고추")))
                .andRespond(withSuccess("""
                        {
                          "organic_results": [
                            {
                              "title": "풋고추 1kg당 20개",
                              "snippet": "풋고추는 1kg 기준 약 20개입니다."
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GatheredQuantityClient client = new SerpApiGatheredQuantityClient(
                restTemplate, "https://serpapi.test/search.json", "test-key", 20);

        OptionalInt result = client.findAveragePackageQuantity("풋고추", "판매 단위");

        assertTrue(result.isPresent());
        assertEquals(20, result.getAsInt());
        server.verify();
    }
}
