import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import ReportDetailView from "./ReportDetailView.vue"
import ReportListView from "./ReportListView.vue"
import { crawlNewsNow, generateMissingReports, getReportDetail, getReports } from "@/api/reportApi"

const mocks = vi.hoisted(() => ({
  routeParams: { newsId: "42" },
  markReportAsRead: vi.fn(),
}))

vi.mock("vue-router", () => ({
  RouterLink: {
    props: ["to"],
    template: '<a :href="to"><slot /></a>',
  },
  useRoute: () => ({ params: mocks.routeParams }),
}))

vi.mock("@/api/reportApi", () => ({
  crawlNewsNow: vi.fn(),
  generateMissingReports: vi.fn(),
  getReportDetail: vi.fn(),
  getReports: vi.fn(),
}))

vi.mock("@/utils/report/reportReadState", () => ({
  getReadReportIds: vi.fn(() => new Set()),
  markReportAsRead: mocks.markReportAsRead,
}))

const reportDetail = {
  id: 42,
  title: "기준금리 변화와 가계 영향",
  category: "금융",
  source: "Wallo",
  publishedAt: "2026-08-14T09:00:00",
  summaryPoints: ["금리 변화가 가계 부담에 영향을 줍니다."],
  eventDescription: "시장 금리가 변동했습니다.",
  cause: "시장 상황이 바뀌었기 때문입니다.",
  socialImpact: "가계의 금융 비용이 달라질 수 있습니다.",
  userImpact: "대출과 예금 계획을 점검할 필요가 있습니다.",
  actionPlan: "고정 지출과 금리 조건을 확인하세요.",
  terms: [],
}

const listReport = {
  id: 42,
  title: "기준금리 변화와 가계 영향",
  category: "금융",
}

const globalStubs = {
  ReportSection: {
    props: ["title", "content", "segments"],
    template:
      '<section class="stub-report-section"><h2>{{ title }}</h2><p>{{ content }}</p></section>',
  },
  TermInfoPanel: {
    props: ["term", "closable"],
    template: '<div class="stub-term-info">{{ term?.term || "용어" }}</div>',
  },
}

describe("report views", () => {
  beforeEach(() => {
    mocks.routeParams.newsId = "42"
    mocks.markReportAsRead.mockReset()
    getReports.mockResolvedValue([listReport])
    crawlNewsNow.mockResolvedValue({ started: true, crawledNewsCount: 2 })
    generateMissingReports.mockResolvedValue({ newlyStarted: false })
    getReportDetail.mockResolvedValue(reportDetail)
    vi.stubGlobal("alert", vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it("loads the report list and refreshes after crawling news", async () => {
    const wrapper = mount(ReportListView, { global: { stubs: globalStubs } })

    await flushPromises()

    expect(getReports).toHaveBeenCalledOnce()
    expect(wrapper.find(".app-page-header").exists()).toBe(true)
    expect(wrapper.find(".app-page-header__title").text()).toBe("금융 리포트")
    expect(wrapper.find(".report-crawl-button").classes()).toContain("app-button")
    expect(wrapper.find(".report-generate-button").classes()).toContain("app-button")
    expect(wrapper.find(".report-list-grid").exists()).toBe(true)
    expect(wrapper.find(".report-card").classes()).toContain("app-card")
    expect(wrapper.text()).toContain("기준금리 변화와 가계 영향")

    await wrapper.get(".report-crawl-button").trigger("click")
    await flushPromises()

    expect(crawlNewsNow).toHaveBeenCalledOnce()
    expect(getReports).toHaveBeenCalledTimes(2)
    expect(wrapper.find(".report-generation-message").classes()).toContain("app-alert")
    expect(wrapper.find(".report-generation-message").classes()).toContain("app-alert--info")
    expect(wrapper.find(".report-generation-message").attributes("role")).toBe("status")
    expect(wrapper.text()).toContain("새 뉴스 2건을 수집했습니다.")

    wrapper.unmount()
  })

  it("loads report detail and marks the report as read", async () => {
    const wrapper = mount(ReportDetailView, { global: { stubs: globalStubs } })

    await flushPromises()

    expect(getReportDetail).toHaveBeenCalledWith("42")
    expect(mocks.markReportAsRead).toHaveBeenCalledWith("42")
    expect(wrapper.find(".app-page-header").exists()).toBe(true)
    expect(wrapper.find(".app-page-header__title").text()).toBe("금융 리포트 상세")
    expect(wrapper.find(".report-detail-card").exists()).toBe(true)
    expect(wrapper.find(".report-detail-card").classes()).toContain("app-card")
    expect(wrapper.find(".report-detail-card h2").text()).toContain("기준금리 변화와 가계 영향")
    expect(wrapper.text()).toContain("금리 변화가 가계 부담에 영향을 줍니다.")
    expect(wrapper.findAll(".stub-report-section")).toHaveLength(5)

    wrapper.unmount()
  })
})
