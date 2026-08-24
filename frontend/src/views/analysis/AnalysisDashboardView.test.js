import { flushPromises, mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { beforeEach, describe, expect, it, vi } from "vitest"
import AnalysisDashboardView from "./AnalysisDashboardView.vue"
import { getLatestAnalysisResults } from "@/api/analysisResultApi"
import { getLatestProductRecommendation } from "@/api/productRecommendationApi"

const push = vi.fn()

vi.mock("vue-router", () => ({
  useRouter: () => ({ push }),
}))

vi.mock("@/api/analysisResultApi", () => ({
  getLatestAnalysisResults: vi.fn(),
}))

vi.mock("@/api/productRecommendationApi", () => ({
  getLatestProductRecommendation: vi.fn(),
}))

const globalStubs = {
  AnalysisResult: {
    props: ["analysis"],
    template: '<div data-testid="consumption-analysis">{{ analysis.marker }}</div>',
  },
  AssetAnalysisResult: {
    props: ["analysis", "showIntro"],
    template: '<div data-testid="asset-analysis" :data-show-intro="showIntro">{{ analysis.marker }}</div>',
  },
  ProductRecommendationResult: {
    props: ["recommendation"],
    template: '<div data-testid="product-recommendation">{{ recommendation.products[0].productName }}</div>',
  },
}

describe("AnalysisDashboardView", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getLatestAnalysisResults.mockResolvedValue({
      asset: {
        assetAnalysis: { marker: "자산 결과" },
        generatedAt: "2026-08-20T10:00:00",
      },
      consumption: {
        consumptionAnalysis: { marker: "소비 결과" },
        generatedAt: "2026-08-20T11:00:00",
      },
    })
    getLatestProductRecommendation.mockResolvedValue({
      data: {
        requestMessage: "예금 추천",
        productRecommendation: {
          productType: "예금",
          products: [{ productName: "안심 정기예금" }],
        },
        generatedAt: "2026-08-20T12:00:00",
      },
    })
  })

  it("shows one analysis result at a time and switches it with buttons", async () => {
    const wrapper = mount(AnalysisDashboardView, { global: { stubs: globalStubs } })
    await flushPromises()

    expect(wrapper.get('[data-testid="analysis-refresh-button"]').classes()).toContain(
      "app-action-link",
    )
    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs).toHaveLength(3)
    expect(tabs.map((tab) => tab.text())).toEqual(["자산분석", "소비분석", "금융상품추천"])
    expect(wrapper.get('[data-testid="asset-analysis"]').text()).toContain("자산 결과")
    expect(wrapper.get('[data-testid="asset-analysis"]').attributes("data-show-intro")).toBe("false")

    expect(wrapper.find('[data-testid="consumption-analysis"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="product-recommendation"]').exists()).toBe(false)

    await tabs[1].trigger("click")
    expect(wrapper.get('[data-testid="consumption-analysis"]').text()).toContain("소비 결과")
    expect(wrapper.find('[data-testid="asset-analysis"]').exists()).toBe(false)

    await tabs[2].trigger("click")
    expect(wrapper.get('[data-testid="product-recommendation"]').text()).toContain("안심 정기예금")
    expect(wrapper.find('[data-testid="consumption-analysis"]').exists()).toBe(false)
  })

  it("offers chat actions when no saved results exist", async () => {
    getLatestAnalysisResults.mockResolvedValue({ asset: null, consumption: null })
    getLatestProductRecommendation.mockResolvedValue({ data: null })
    const wrapper = mount(AnalysisDashboardView, { global: { stubs: globalStubs } })
    await flushPromises()

    expect(wrapper.text()).toContain("저장된 자산분석이 없습니다.")
    expect(wrapper.text()).not.toContain("저장된 소비분석이 없습니다.")
    expect(wrapper.find('[role="tabpanel"] .app-state__icon').exists()).toBe(false)
    expect(wrapper.find('[role="tabpanel"] .goal-button').exists()).toBe(true)

    await wrapper.find(".app-state__actions button").trigger("click")
    expect(push).toHaveBeenCalledWith({ name: "chat", query: { action: "asset-analysis" } })

    await wrapper.findAll('[role="tab"]')[1].trigger("click")
    expect(wrapper.text()).toContain("저장된 소비분석이 없습니다.")
    expect(wrapper.find('[role="tabpanel"] .app-state__icon').exists()).toBe(false)
    expect(wrapper.find('[role="tabpanel"] .goal-button').exists()).toBe(true)

    await wrapper.findAll('[role="tab"]')[2].trigger("click")
    expect(wrapper.text()).toContain("저장된 금융상품 추천이 없습니다.")
    expect(wrapper.find('[role="tabpanel"] .app-state__icon').exists()).toBe(false)
    expect(wrapper.find('[role="tabpanel"] .goal-button').exists()).toBe(true)
  })
})
