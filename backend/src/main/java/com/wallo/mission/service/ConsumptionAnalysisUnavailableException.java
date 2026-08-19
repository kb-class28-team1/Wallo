package com.wallo.mission.service;

/** 맞춤 미션 생성에 필요한 소비 분석이 아직 없는 정상적인 초기 상태를 나타냄. */
public class ConsumptionAnalysisUnavailableException extends IllegalStateException {
    public ConsumptionAnalysisUnavailableException() {
        super("Consumption analysis is unavailable.");
    }
}
