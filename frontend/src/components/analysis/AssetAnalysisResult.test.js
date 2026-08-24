import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"

import AssetAnalysisResult from "./AssetAnalysisResult.vue"

const analysis = {
  summary: {
    totalAssetsKrw: 120000000,
    totalDebtKrw: 20000000,
    netAssetsKrw: 100000000,
  },
  cashflow: {
    monthlyNetIncomeKrw: 4500000,
    monthlySavingKrw: 1200000,
    monthlyExpenseKrw: 3300000,
    monthlySurplusKrw: 1200000,
    annualSavingKrw: 14400000,
    savingRatePercent: 26.7,
  },
  composition: [
    {
      name: "Savings account",
      category: "saving_cash",
      amountKrw: 80000000,
      sharePercent: 66.7,
      estimated: false,
    },
    {
      name: "Investment account",
      category: "investment",
      amountMinKrw: 15000000,
      amountMaxKrw: 25000000,
      sharePercent: 20.8,
      estimated: true,
    },
  ],
  dataQualityNotes: ["Some asset values are estimated."],
}

describe("AssetAnalysisResult", () => {
  it("can hide the introductory copy when embedded in chat", () => {
    const wrapper = mount(AssetAnalysisResult, {
      props: { analysis, showIntro: false },
    })

    expect(wrapper.find(".asset-analysis__intro").exists()).toBe(false)
    expect(wrapper.text()).not.toContain("현재 자산 상태를 한눈에 확인해보세요")
    expect(wrapper.text()).toContain("자산 현황")
  })

  it("shows a concise non-repeating report in compact mode", () => {
    const wrapper = mount(AssetAnalysisResult, {
      props: {
        compact: true,
        analysis: {
          ...analysis,
          composition: [
            { name: "STOCK", category: "STOCK", sharePercent: 49.6 },
            { name: "SAVINGS", category: "SAVINGS", sharePercent: 30.2 },
            { name: "DEPOSIT", category: "DEPOSIT", sharePercent: 20.2 },
          ],
          direction: { riskSignals: ["부채 비중을 확인하세요."] },
          priorityActions: [{ title: "비상금 점검", description: "생활비 3개월분을 준비하세요." }],
        },
      },
    })

    expect(wrapper.text()).toContain("현재 순자산")
    expect(wrapper.text()).toContain("월 잉여금")
    expect(wrapper.text()).toContain("주식")
    expect(wrapper.text()).toContain("적금")
    expect(wrapper.text()).toContain("예금")
    expect(wrapper.text()).not.toContain("STOCK")
    expect(wrapper.text()).not.toContain("SAVINGS")
    expect(wrapper.text()).not.toContain("DEPOSIT")
    expect(wrapper.text()).toContain("비상금 점검")
    expect(wrapper.text()).toContain("부채 비중을 확인하세요.")
    expect(wrapper.text()).not.toContain("자산 구성")
    expect(wrapper.text()).not.toContain("AI 진단")
    expect(wrapper.text()).not.toContain("추천 행동")
    expect(wrapper.findAll(".asset-overview__metrics > div")).toHaveLength(2)
    expect(wrapper.find(".asset-insight--risk").exists()).toBe(true)
    expect(wrapper.find(".asset-analysis-card").exists()).toBe(false)
    expect(wrapper.find(".analysis-detail-toggle").exists()).toBe(false)
    expect(wrapper.text().match(/총자산/g)).toHaveLength(1)
    expect(wrapper.text().match(/월 잉여금/g)).toHaveLength(1)
  })

  it("renders summary, cashflow, and composition cards", () => {
    const wrapper = mount(AssetAnalysisResult, {
      props: { analysis },
    })

    expect(wrapper.find(".asset-analysis").exists()).toBe(true)
    expect(wrapper.findAll(".asset-analysis-card")).toHaveLength(4)
    expect(wrapper.text()).toContain("120,000,000")
    expect(wrapper.text()).toContain("20,000,000")
    expect(wrapper.text()).toContain("100,000,000")
    expect(wrapper.text()).toContain("4,500,000")
    expect(wrapper.text()).toContain("26.7%")
    expect(wrapper.text()).toContain("80,000,000")
    expect(wrapper.text()).toContain("15,000,000")
    expect(wrapper.text()).toContain("25,000,000")
    expect(wrapper.text()).toContain("Some asset values are estimated.")
    expect(wrapper.findAll('[role="progressbar"]').length).toBeGreaterThan(0)
  })

  it("formats database snapshot timestamps in the reference card", () => {
    const wrapper = mount(AssetAnalysisResult, {
      props: {
        analysis: {
          ...analysis,
          dataQualityNotes: [
            "연동 DB 자산 데이터 기준 시각: 2026-08-22T17:25:33.456732800+09:00",
          ],
        },
      },
    })

    const referenceCard = wrapper.find(".asset-analysis-card--notes")
    expect(referenceCard.exists()).toBe(true)
    expect(referenceCard.text()).toContain("2026년 8월 22일 오후 5:25")
    expect(referenceCard.text()).not.toContain("T17:25:33.456732800+09:00")
  })

  it("uses Korean asset labels without duplicate metadata or action periods", () => {
    const wrapper = mount(AssetAnalysisResult, {
      props: {
        analysis: {
          ...analysis,
          composition: [
            { name: "STOCK", category: "STOCK", amountKrw: 1_000_000, sharePercent: 20 },
            { name: "SAVINGS", category: "SAVINGS", amountKrw: 2_000_000, sharePercent: 40 },
            { name: "DEPOSIT", category: "DEPOSIT", amountKrw: 2_000_000, sharePercent: 40 },
          ],
          dataQualityNotes: [],
          priorityActions: [
            { period: "3개월", title: "비상금 점검", description: "생활비를 확인하세요." },
          ],
        },
      },
    })

    expect(wrapper.text()).toContain("주식")
    expect(wrapper.text()).toContain("적금")
    expect(wrapper.text()).toContain("예금")
    expect(wrapper.text()).not.toContain("STOCK")
    expect(wrapper.text()).not.toContain("SAVINGS")
    expect(wrapper.text()).not.toContain("DEPOSIT")
    expect(wrapper.text()).not.toContain("추정")
    expect(wrapper.find(".asset-composition-item .min-w-0 small").exists()).toBe(false)
    expect(wrapper.find(".asset-priority-action__period").exists()).toBe(false)
    expect(wrapper.findAll(".asset-analysis-card__eyebrow")).toHaveLength(0)

    const stockItem = wrapper
      .findAll(".asset-composition-item")
      .find((item) => item.text().includes("주식"))
    const stockSummary = stockItem.find(".asset-composition-item__summary")
    expect(stockSummary.find(".asset-composition-item__name").text()).toBe("주식")
    expect(stockSummary.find(".asset-composition-item__share").text()).toBe("20%")
    expect(stockSummary.find(".asset-composition-item__amount").text()).toBe("1,000,000원")
  })

  it("shows the empty state when no usable analysis data exists", () => {
    const wrapper = mount(AssetAnalysisResult, {
      props: { analysis: {} },
    })

    expect(wrapper.find('[role="status"]').exists()).toBe(true)
    expect(wrapper.findAll(".asset-analysis-card")).toHaveLength(0)
  })

  it("renders direction, priority actions, and caution cards", () => {
    const wrapper = mount(AssetAnalysisResult, {
      props: {
        analysis: {
          direction: {
            headline: "현금흐름을 지키면서 장기 성장을 준비해보세요.",
            currentStage: "안전망 확보",
            reasons: ["월 저축액이 확인됐어요."],
            keep: "현재의 꾸준한 저축을 유지하세요.",
            firstChange: "단기 목적 자금과 투자자금을 분리하세요.",
            riskSignals: ["목표 기간에 따라 투자 비중을 조정해야 해요."],
            additionalInfo: ["목표 시점이 필요해요."],
          },
          priorityActions: [
            { title: "가장 먼저 바꿀 것", description: "비상금을 점검하세요." },
            { title: "3개월 실행 방향", description: "생활비 기준의 안전자금을 확인하세요." },
            { title: "1년 실행 방향", description: "장기 투자 비중을 점검하세요." },
          ],
        },
      },
    })

    expect(wrapper.find(".asset-analysis-card--direction").exists()).toBe(true)
    expect(wrapper.find(".asset-priority-action").exists()).toBe(true)
    expect(wrapper.find(".asset-analysis-card--warning").exists()).toBe(true)
    expect(wrapper.text()).toContain("안전망 확보")
    expect(wrapper.text()).toContain("목표 기간에 따라 투자 비중을 조정해야 해요.")
    expect(wrapper.findAll(".asset-priority-action__title").map((item) => item.text().replace(/\s+/g, " ").trim()))
      .toEqual(["가장 먼저 바꿀 것", "3개월 실행 방향", "1년 실행 방향"])
    expect(wrapper.findAll(".asset-priority-action__title-accent").map((item) => item.text()))
      .toEqual(["가장 먼저", "3개월", "1년"])
  })
})
