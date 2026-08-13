package com.wallo.feed.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class SerpApiShoppingPriceClientTest {

    @Test
    void returnsOnlyNormalOneTimePurchasePrices() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> {
                    java.net.URI uri = request.getURI();
                    String query = uri.getRawQuery();
                    assertTrue(query.contains("engine=google_shopping"));
                    assertTrue(query.contains("gl=kr"));
                    assertTrue(query.contains("hl=ko"));
                    assertTrue(query.contains("api_key=test-key"));
                })
                .andRespond(withSuccess("""
                        {
                          "shopping_results": [
                            {
                              "title": "서울우유 나100% 1L",
                              "extracted_price": 2360,
                              "source": "A마트",
                              "product_link": "https://example.com/milk",
                              "delivery": "무료배송"
                            },
                            {
                              "title": "중고 우유 냉장고",
                              "extracted_price": 1000,
                              "source": "중고몰",
                              "product_link": "https://example.com/used",
                              "second_hand_condition": "used"
                            },
                            {
                              "title": "우유 정기 결제",
                              "extracted_price": 500,
                              "source": "구독몰",
                              "product_link": "https://example.com/monthly",
                              "installment": {"period": 12}
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        SerpApiShoppingPriceClient client = new SerpApiShoppingPriceClient(
                restTemplate, "https://serpapi.test/search.json", "test-key", 20);

        List<ShoppingPriceCandidate> results = client.search("우유", "서울우유", "1L");

        assertEquals(1, results.size());
        assertEquals(2_360, results.get(0).price());
        assertEquals("A마트", results.get(0).source());
        server.verify();
    }

    @Test
    void returnsEmptyListWhenProviderFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> { })
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("timeout");
                });
        SerpApiShoppingPriceClient client = new SerpApiShoppingPriceClient(
                restTemplate, "https://serpapi.test/search.json", "test-key", 20);

        assertTrue(client.search("우유", "", "1L").isEmpty());
        server.verify();
    }
}
