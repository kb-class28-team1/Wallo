import { ref } from "vue"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { flushPromises, mount } from "@vue/test-utils"
import ConsumptionReportCard from "./ConsumptionReportCard.vue"
import { useReportStore } from "@/stores/assetReportStore.js"

vi.mock("@/stores/assetReportStore.js", () => ({
  useReportStore: vi.fn(),
}))

const globalStubs = {
  RouterLink: {
    props: ["to"],
    template: '<a :href="to"><slot /></a>',
  },
}

const createStore = () => ({
  insight: ref(null),
  initialInsightLoading: ref(false),
  refreshingInsight: ref(false),
  isInsightLoading: ref(false),
  insightError: ref(null),
  fetchInsight: vi.fn().mockResolvedValue(null),
})

describe("ConsumptionReportCard", () => {
  let store

  beforeEach(() => {
    store = createStore()
    useReportStore.mockReturnValue(store)
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it("shows the loaded report and the dashboard-style more link", async () => {
    store.insight.value = {
      category: "FOOD",
      reportTitle: "이번 달 소비 리포트",
      reportContent: "지출 내역을 점검해보세요.",
      generationMode: "AI",
    }

    const wrapper = mount(ConsumptionReportCard, {
      global: { stubs: globalStubs },
    })
    await flushPromises()

    expect(wrapper.find(".consumption-report-card").classes()).toContain("app-card")
    expect(wrapper.find(".report-content").exists()).toBe(true)
    expect(wrapper.text()).toContain("이번 달 소비 리포트")
    expect(wrapper.text()).toContain("지출 내역을 점검해보세요.")
    expect(wrapper.get(".app-action-link").text()).toContain("더보기")
    expect(wrapper.get(".app-action-link").attributes("href")).toBe("/assets/expenses")
    expect(wrapper.find(".report-detail-link").exists()).toBe(false)
    expect(store.fetchInsight).toHaveBeenCalledWith({ force: false })
  })

  it("shows the shared error state and retries with a forced request", async () => {
    store.insightError.value = "리포트 조회 실패"

    const wrapper = mount(ConsumptionReportCard, {
      global: { stubs: globalStubs },
    })
    await flushPromises()

    expect(wrapper.find(".report-state").classes()).toContain("app-state")
    expect(wrapper.find(".report-state").classes()).toContain("app-state--error")
    expect(wrapper.find('[role="alert"]').text()).toContain("리포트 조회 실패")

    await wrapper.get(".report-state .app-button").trigger("click")
    await flushPromises()

    expect(store.fetchInsight).toHaveBeenLastCalledWith({ force: true })
  })

  it("shows the shared empty state when there is no report data", async () => {
    const wrapper = mount(ConsumptionReportCard, {
      global: { stubs: globalStubs },
    })
    await flushPromises()

    expect(wrapper.find(".report-state").classes()).toContain("app-state")
    expect(wrapper.find(".report-state").classes()).toContain("app-state--empty")
    expect(wrapper.text()).toContain("분석할 소비 데이터가 없습니다.")
    expect(wrapper.text()).toContain("소비 내역이 쌓이면 맞춤형 리포트를 확인할 수 있습니다.")
  })
})
