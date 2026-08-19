package com.wallo.mission.verification;

import com.wallo.mission.dto.MissionVerificationDto;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 미션 문장과 챌린지 AI 분석 문장에서 여러 단어가 겹치는지 판정한다. */
public class TextOverlapMissionVerificationClient implements MissionVerificationClient {
    private static final Logger log = LoggerFactory.getLogger(
            TextOverlapMissionVerificationClient.class);
    private static final int REQUIRED_MATCH_COUNT = 1;
    private static final Set<String> STOP_WORDS = Set.of(
            "오늘", "미션", "하기", "하다", "사용자", "사진", "영상", "화면", "확인",
            "분석", "결과", "예상", "절약", "금액", "직접", "통해", "대한", "위해",
            "있다", "없다", "그리고", "정도", "하나", "한번");
    private static final Set<String> MEANINGFUL_SINGLE_WORDS = Set.of("물", "밥", "차", "집");
    private static final String[] PARTICLES = {
            "으로부터", "에게서", "에서는", "으로", "에서", "에게", "한테", "처럼",
            "보다", "까지", "부터", "이나", "거나", "하고", "이며", "이고",
            "은", "는", "이", "가", "을", "를", "에", "의", "와", "과", "도", "만", "로"
    };

    @Override
    public MissionVerificationDto.AiResult verify(
            String analysisSummary, MissionVerificationDto.MissionSpec mission) {
        Set<String> missionWords = words(mission.title() + " " + mission.description());
        Set<String> analysisWords = words(analysisSummary);
        Set<String> matched = missionWords.stream()
                .filter(analysisWords::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String decision = matched.size() >= REQUIRED_MATCH_COUNT ? "PASS" : "FAIL";
        log.info("[MISSION_MATCH] dailyMissionId={} title={} analysis={} "
                        + "missionWords={} analysisWords={} matchedWords={} decision={}",
                mission.dailyMissionId(), mission.title(), analysisSummary,
                missionWords, analysisWords, matched, decision);

        if (matched.size() < REQUIRED_MATCH_COUNT) {
            String detail = matched.isEmpty() ? "없음" : String.join(", ", matched);
            return new MissionVerificationDto.AiResult(
                    "FAIL", 1.0,
                    "미션 문장과 챌린지 AI 분석 결과에서 일치하는 단어를 찾지 못했습니다. "
                            + "일치 단어: " + detail + " (필요: " + REQUIRED_MATCH_COUNT + "개)",
                    "text-overlap-v2");
        }
        return new MissionVerificationDto.AiResult(
                "PASS", 1.0,
                "미션 문장과 챌린지 AI 분석 결과에서 단어(" + String.join(", ", matched)
                        + ")가 일치했습니다.",
                "text-overlap-v2");
    }

    private Set<String> words(String text) {
        return Arrays.stream(normalize(text).split("\\s+"))
                .map(this::removeParticle)
                .filter(word -> !word.isBlank() && !STOP_WORDS.contains(word))
                .filter(word -> word.length() >= 2 || MEANINGFUL_SINGLE_WORDS.contains(word))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String removeParticle(String word) {
        for (String particle : PARTICLES) {
            if (word.length() > particle.length() && word.endsWith(particle)) {
                return word.substring(0, word.length() - particle.length());
            }
        }
        return word;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-z가-힣]+", " ")
                .trim();
    }
}
