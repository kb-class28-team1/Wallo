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
})
