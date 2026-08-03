package com.wallo.challenge.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.challenge.domain.MyFeed;
import com.wallo.challenge.dto.response.MyFeedListResponse;
import com.wallo.challenge.service.MyFeedService;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MyFeedControllerTest {

    private MyFeedService myFeedService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        myFeedService = mock(MyFeedService.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        MyFeedController controller = new MyFeedController(myFeedService, currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsCurrentUsersFeedsWithDefaultConditions() throws Exception {
        MyFeedListResponse response = MyFeedListResponse.of(
                Collections.singletonList(feed()),
                0,
                10,
                1);

        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        when(myFeedService.getMyFeeds(1L, "LIKE_DESC", "ALL", 0, 10))
                .thenReturn(response);

        mockMvc.perform(get("/api/users/me/feeds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].feedId").value(100))
                .andExpect(jsonPath("$.content[0].challengeId").value(10))
                .andExpect(jsonPath("$.content[0].caption").value("도시락 싸기 일주일"))
                .andExpect(jsonPath("$.content[0].likeCount").value(44))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(currentUserProvider).getCurrentUserId();
        verify(myFeedService).getMyFeeds(1L, "LIKE_DESC", "ALL", 0, 10);
    }

    @Test
    void passesRequestedConditionsToService() throws Exception {
        MyFeedListResponse response = MyFeedListResponse.of(
                Collections.emptyList(),
                2,
                5,
                0);

        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        when(myFeedService.getMyFeeds(1L, "LATEST", "CAFE", 2, 5))
                .thenReturn(response);

        mockMvc.perform(get("/api/users/me/feeds")
                        .param("sort", "LATEST")
                        .param("category", "CAFE")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(myFeedService).getMyFeeds(1L, "LATEST", "CAFE", 2, 5);
    }

    private MyFeed feed() {
        MyFeed feed = new MyFeed();
        feed.setFeedId(100L);
        feed.setChallengeId(10L);
        feed.setCaption("도시락 싸기 일주일");
        feed.setCategory("FOOD");
        feed.setSavingAmount(38500L);
        feed.setLikeCount(44);
        feed.setCommentCount(16);
        feed.setCreatedAt(LocalDateTime.of(2026, 7, 31, 9, 30));
        return feed;
    }
}
