package com.wallo.feed.service;

/** 분석 파이프라인의 현재 단계와 진행률을 전달하는 콜백. */
@FunctionalInterface
public interface AnalysisProgressListener {
    void update(int progress, String message);
}
