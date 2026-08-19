package com.wallo.mission.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.mission.dto.MissionVerificationDto;
import org.junit.jupiter.api.Test;

class TextOverlapMissionVerificationClientTest {
    private final TextOverlapMissionVerificationClient client =
            new TextOverlapMissionVerificationClient();

    @Test
    void passesWhenAtLeastTwoWordsMatchAfterRemovingParticles() {
        var result = client.verify(
                "사용자가 집에서 샐러드가 담긴 식사를 준비했습니다. 예상 절약 금액 6000원",
                mission("건강한 식단 먹기", "집에서 건강한 샐러드를 만들어 먹으세요."));

        assertEquals("PASS", result.decision(), result.reason());
        assertTrue(result.reason().contains("집"));
        assertTrue(result.reason().contains("샐러드"));
    }

    @Test
    void passesWhenOnlyOneWordMatches() {
        var result = client.verify(
                "사용자가 집에서 버스를 기다렸습니다.",
                mission("건강한 식단 먹기", "집에서 건강한 샐러드를 만들어 먹으세요."));

        assertEquals("PASS", result.decision());
        assertTrue(result.reason().contains("집"));
    }

    @Test
    void failsWhenNoWordsMatch() {
        var result = client.verify(
                "사용자가 버스를 타고 이동했습니다.",
                mission("건강한 식단 먹기", "집에서 건강한 샐러드를 만들어 먹으세요."));

        assertEquals("FAIL", result.decision());
        assertTrue(result.reason().contains("일치하는 단어를 찾지 못했습니다"));
    }

    private MissionVerificationDto.MissionSpec mission(String title, String description) {
        return new MissionVerificationDto.MissionSpec(
                1L, title, description, null, "{\"description\":\"test\"}");
    }
}
