import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"
import AnalysisResult from "./AnalysisResult.vue"

const baseAnalysis = {
  period: { type: "MONTHLY", label: "이번 달" },
  hasEnoughData: true,
  insufficient: null,
  summary: {
    currentTotal: 142500,
    previousTotal: 408500,
    deltaAmount: -266000,
    deltaRate: -65.1,
    warningIncrease: false,
  },
  signals: {
    categoryOverview: [{
      categoryCode: "TRANSPORT",
      transactionCount: 1,
      maxTransactionAmount: 30000,
      previous: 0,
      current: 30000,
      deltaAmount: 30000,
      deltaRate: null,
    }],
    criteriaEvaluations: [{
      code: "DATA_SUFFICIENCY",
      evaluated: true,
      detected: true,
      metrics: { transactionCount: 5, minimumTransactionCount: 3 },
    }],
    categorySpikes: [],
    newSpendings: [],
    oneTimeLarge: [{
      categoryCode: "TRANSPORT",
      maxTransactionAmount: 30000,
      categoryAmount: 30000,
      shareOfTotalPercent: 21.1,
      excludedFromMission: true,
    }],
    budget: null,
    recurringPatterns: [],
    subscriptions: [],
    positives: [{
      categoryCode: "SHOPPING",
      decreaseAmount: 94000,
      decreaseRate: 83.6,
    }],
    streaks: [],
  },
}

describe("AnalysisResult", () => {
  it("shows a concise spending overview and expands the full analysis", async () => {
    const wrapper = mount(AnalysisResult, {
      props: { compact: true, analysis: baseAnalysis },
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    })

    expect(wrapper.text()).toContain("이번 달 총지출")
    expect(wrapper.text()).toContain("저번 달보다 266,000원 줄었어요")
    expect(wrapper.text()).toContain("지출이 큰 카테고리")
    expect(wrapper.text()).toContain("교통")
    expect(wrapper.find(".analysis-card").exists()).toBe(false)

    await wrapper.get(".analysis-detail-toggle").trigger("click")
    expect(wrapper.find(".analysis-card").exists()).toBe(true)
    expect(wrapper.text()).toContain("상세 내용 접기")
  })

  it("uses a spending illustration for the monthly report shortcut", () => {
    const wrapper = mount(AnalysisResult, {
      props: { analysis: { hasEnoughData: false, signals: {} } },
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    })

    const image = wrapper.get(".report-link__icon img")
    expect(image.attributes("src")).toMatch(/^\/images\/spending\/.+\.webp$/)
    expect(image.attributes("alt")).toBe("")
  })

  it("서버가 전달한 신호가 있는 블록만 표시한다", () => {
    const wrapper = mount(AnalysisResult, {
      props: { analysis: baseAnalysis },
    })

    expect(wrapper.text()).toContain("142,500원")
    expect(wrapper.text()).toContain("잘한 점")
    expect(wrapper.text()).toContain("쇼핑")
    expect(wrapper.text()).toContain("30,000원")
    expect(wrapper.text()).toContain("저번 달 지출 없음")
    expect(wrapper.text()).not.toContain("비교 기간")
    expect(wrapper.text()).not.toContain("전체 소비 비중")
    expect(wrapper.text()).not.toContain("예산 사용 현황")
  })

  it("분석 유형에 맞는 비교 기간 이름을 표시한다", () => {
    const weeklyWrapper = mount(AnalysisResult, {
      props: {
        analysis: {
          ...baseAnalysis,
          period: { type: "WEEKLY", label: "이번 주" },
        },
      },
    })
    const customWrapper = mount(AnalysisResult, {
      props: {
        analysis: {
          ...baseAnalysis,
          period: { type: "CUSTOM", label: "지정 기간" },
        },
      },
    })

    expect(weeklyWrapper.text()).toContain("저번 주보다 266,000원 덜 썼어요")
    expect(customWrapper.text()).toContain("이전 기간보다 266,000원 덜 썼어요")
  })

  it("반복 소비는 카테고리명 옆 아이콘으로 표시한다", () => {
    const wrapper = mount(AnalysisResult, {
      props: {
        analysis: {
          ...baseAnalysis,
          signals: {
            ...baseAnalysis.signals,
            repeatingCategories: [{ categoryCode: "TRANSPORT" }],
          },
        },
      },
    })

    const categoryRow = wrapper.get(".category-row")
    expect(categoryRow.find(".bi-arrow-repeat").exists()).toBe(true)
    expect(categoryRow.find(".badge").exists()).toBe(false)
    expect(categoryRow.text()).not.toContain("반복 소비")
  })

  it("정기결제 후보의 반복 횟수와 금액을 고정된 열에 표시한다", () => {
    const wrapper = mount(AnalysisResult, {
      props: {
        analysis: {
          ...baseAnalysis,
          focus: "SUBSCRIPTION",
          signals: {
            ...baseAnalysis.signals,
            subscriptions: [
              { merchant: "영상 구독", count: 3, amount: 12000 },
              { merchant: "음악 구독", count: 10, amount: 8900 },
            ],
          },
        },
      },
    })

    const rows = wrapper.findAll(".subscription-row")
    expect(rows).toHaveLength(2)
    expect(rows[0].find(".subscription-row__count").text()).toBe("3회 반복")
    expect(rows[0].find(".subscription-row__amount").text()).toBe("12,000원")
    expect(rows[1].find(".subscription-row__count").text()).toBe("10회 반복")
  })

  it("데이터가 부족해도 안내와 계산 가능한 결과를 함께 표시한다", () => {
    const wrapper = mount(AnalysisResult, {
      props: {
        analysis: {
          ...baseAnalysis,
          hasEnoughData: false,
          summary: null,
          insufficient: {
            txCount: 2,
            totalAmount: 10000,
            message: "거래가 충분하지 않아요",
          },
        },
      },
    })

    expect(wrapper.text()).toContain("거래가 충분하지 않아요")
    expect(wrapper.text()).toContain("카테고리별 소비 내역")
    expect(wrapper.text()).not.toContain("전체 분석 기준")
  })

  it("패턴 질문에는 반복 소비 패턴 카드만 표시한다", () => {
    const wrapper = mount(AnalysisResult, {
      props: {
        analysis: {
          ...baseAnalysis,
          focus: "PATTERN",
          signals: {
            ...baseAnalysis.signals,
            recurringPatterns: [{
              type: "WEEKEND",
              weekdayAvg: 10000,
              weekendAvg: 30000,
            }],
          },
        },
      },
    })

    expect(wrapper.text()).toContain("반복 소비 패턴")
    expect(wrapper.text()).toContain("최근 4주 동안")
    expect(wrapper.get(".text-primary.fw-semibold").text()).toBe("주말")
    expect(wrapper.text()).not.toContain("카테고리별 소비 내역")
    expect(wrapper.text()).not.toContain("잘한 점")
    expect(wrapper.text()).not.toContain("142,500원")
  })

  it("예산 질문에 해당 월 예산이 없으면 안내한다", () => {
    const wrapper = mount(AnalysisResult, {
      props: { analysis: { ...baseAnalysis, focus: "BUDGET" } },
    })

    expect(wrapper.text()).toContain("해당 분석 월에 설정된 예산이 없어요")
    expect(wrapper.text()).toContain("142,500원")
    expect(wrapper.text()).not.toContain("카테고리별 소비 내역")
  })
})
