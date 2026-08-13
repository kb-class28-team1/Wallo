package com.wallo.feed.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class SerpApiRestaurantPriceClientTest {

    @Test
    void extractsWonPricesFromMatchingMenuResults() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> {
                    String query = request.getURI().getRawQuery();
                    assertTrue(query.contains("engine=google"));
                    assertTrue(query.contains("gl=kr"));
                    assertTrue(query.contains("api_key=test-key"));
                })
                .andRespond(withSuccess("""
                        {
                          "organic_results": [
                            {
                              "title": "서울 카페 수제 당고 메뉴",
                              "link": "https://example.com/cafe-a",
                              "source": "카페A",
                              "snippet": "당고 1꼬치 가격은 2,500원입니다."
                            },
                            {
                              "title": "당고 맛집 메뉴판",
                              "link": "https://example.com/cafe-b",
                              "displayed_link": "카페B",
                              "snippet": "당고 한 꼬치 3,000원"
                            },
                            {
                              "title": "관련 없는 메뉴",
                              "link": "https://example.com/other",
                              "snippet": "커피 1,500원"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        SerpApiRestaurantPriceClient client = new SerpApiRestaurantPriceClient(
                restTemplate, "https://serpapi.test/search.json", "test-key", 20);

        List<RestaurantPriceCandidate> results = client.search("당고", "1꼬치");

        assertEquals(2, results.size());
        assertEquals(2_500, results.get(0).price());
        assertEquals(3_000, results.get(1).price());
        assertEquals("카페A", results.get(0).source());
        server.verify();
    }
}
