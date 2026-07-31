package com.wallo.challenge.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.challenge.domain.MonthlySaving;
import com.wallo.challenge.domain.MyChallengeSummary;
import com.wallo.challenge.domain.TopLikedFeed;
import com.wallo.challenge.dto.response.MyChallengeDashboardResponse;
import com.wallo.challenge.service.ChallengeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MyChallengeControllerTest {

    private ChallengeService challengeService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        challengeService = mock(ChallengeService.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        MyChallengeController controller =
                new MyChallengeController(challengeService, currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsCurrentUsersChallengeDashboard() throws Exception {
        MyChallengeSummary summary = summary();
        MonthlySaving monthlySaving = monthlySaving();
        TopLikedFeed topLikedFeed = topLikedFeed();
        MyChallengeDashboardResponse response = MyChallengeDashboardResponse.of(
                summary,
                Collections.singletonList(monthlySaving),
                Collections.singletonList(topLikedFeed));

        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        when(challengeService.getMyChallengeDashboard(1L)).thenReturn(response);

        mockMvc.perform(get("/api/users/me/challenge-dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.nickname").value("김혜진"))
                .andExpect(jsonPath("$.joinedAt").value("2025-01-15"))
                .andExpect(jsonPath("$.currentChallengeId").value(10))
                .andExpect(jsonPath("$.totalSavingAmount").value(1285600))
                .andExpect(jsonPath("$.savingChangeRate").value(12.0))
                .andExpect(jsonPath("$.monthlySavings[0].month").value("2026-07"))
                .andExpect(jsonPath("$.monthlySavings[0].savingAmount").value(45000))
                .andExpect(jsonPath("$.topLikedFeeds[0].feedId").value(100))
                .andExpect(jsonPath("$.topLikedFeeds[0].likeCount").value(28));

        verify(currentUserProvider).getCurrentUserId();
        verify(challengeService).getMyChallengeDashboard(1L);
    }

    private MyChallengeSummary summary() {
        MyChallengeSummary summary = new MyChallengeSummary();
        summary.setUserId(1L);
        summary.setNickname("김혜진");
        summary.setProfileImageUrl("/images/profile.svg");
        summary.setJoinedAt(LocalDate.of(2025, 1, 15));
        summary.setStreakDays(12);
        summary.setCurrentChallengeId(10L);
        summary.setCurrentChallengeName("함께 절약");
        summary.setTotalSavingAmount(1285600L);
        summary.setCurrentMonthSavingAmount(186500L);
        summary.setPreviousMonthSavingAmount(166500L);
        summary.setSavingChangeRate(new BigDecimal("12.0"));
        summary.setVerificationCount(48);
        summary.setAverageSavingAmount(26783L);
        summary.setPostCount(32);
        summary.setReceivedLikeCount(236);
        summary.setCommentCount(58);
        return summary;
    }

    private MonthlySaving monthlySaving() {
        MonthlySaving monthlySaving = new MonthlySaving();
        monthlySaving.setMonth("2026-07");
        monthlySaving.setSavingAmount(45000L);
        return monthlySaving;
    }

    private TopLikedFeed topLikedFeed() {
        TopLikedFeed feed = new TopLikedFeed();
        feed.setFeedId(100L);
        feed.setChallengeId(10L);
        feed.setCaption("오늘의 커피 절약");
        feed.setThumbnailUrl("/images/feed-thumbnail.jpg");
        feed.setMediaUrl("/images/feed.jpg");
        feed.setLikeCount(28);
        feed.setCreatedAt(LocalDateTime.of(2026, 7, 31, 9, 30));
        return feed;
    }
}
