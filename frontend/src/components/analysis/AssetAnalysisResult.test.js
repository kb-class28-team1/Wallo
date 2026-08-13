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
  it("renders summary, cashflow, and composition cards", () => {
    const wrapper = mount(AssetAnalysisResult, {
      props: { analysis },
    })

    expect(wrapper.find(".asset-analysis").exists()).toBe(true)
    expect(wrapper.findAll(".asset-analysis-card")).toHaveLength(3)
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
            {
              period: "3개월",
              title: "현금성 자산 점검",
              description: "생활비 기준의 안전자금을 확인하세요.",
            },
          ],
        },
      },
    })

    expect(wrapper.find(".asset-analysis-card--direction").exists()).toBe(true)
    expect(wrapper.find(".asset-priority-action").exists()).toBe(true)
    expect(wrapper.find(".asset-analysis-card--warning").exists()).toBe(true)
    expect(wrapper.text()).toContain("안전망 확보")
    expect(wrapper.text()).toContain("현금성 자산 점검")
    expect(wrapper.text()).toContain("목표 기간에 따라 투자 비중을 조정해야 해요.")
  })
})
