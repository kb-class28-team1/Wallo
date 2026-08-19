import { mount } from "@vue/test-utils"
import { describe, expect, it, vi } from "vitest"

import AssetSummaryCard from "./AssetSummaryCard.vue"

vi.mock("vue-chartjs", () => ({
  Line: {
    template: '<div data-testid="line-chart"></div>',
  },
}))

const globalOptions = {
  global: {
    stubs: {
      RouterLink: {
        props: ["to"],
        template: '<a :href="to"><slot /></a>',
      },
    },
  },
}

const createChartData = (data = [1_200_000]) => ({
  labels: ["7월"],
  datasets: [
    {
      data,
      backgroundColor: "rgba(129, 112, 255, 0.14)",
    },
  ],
})

describe("AssetSummaryCard", () => {
  it("renders the asset total, change, chart, and shared card shell", () => {
    const wrapper = mount(AssetSummaryCard, {
      ...globalOptions,
      props: {
        assets: {
          totalAssets: 1_200_000,
          previousMonthTotalAssets: 1_000_000,
        },
        chartData: createChartData(),
      },
    })

    expect(wrapper.find(".asset-summary-card").classes()).toContain("app-card")
    expect(wrapper.find(".asset-summary-card").classes()).toContain("app-card--surface")
    expect(wrapper.find(".asset-total").text()).toBe("1,200,000원")
    expect(wrapper.find(".asset-change-positive").text()).toBe("+200,000원")
    expect(wrapper.find('[data-testid="line-chart"]').exists()).toBe(true)
    expect(wrapper.find("a").attributes("href")).toBe("/assets")
  })

  it("shows the empty trend message when there is no previous asset history", () => {
    const wrapper = mount(AssetSummaryCard, {
      ...globalOptions,
      props: {
        assets: {
          totalAssets: 1_200_000,
          previousMonthTotalAssets: 0,
        },
        chartData: createChartData([]),
      },
    })

    expect(wrapper.find(".asset-summary-card").classes()).toContain("app-card")
    expect(wrapper.find(".asset-trend-empty").text()).toBe("자산 변동 데이터가 없습니다.")
    expect(wrapper.find('[data-testid="line-chart"]').exists()).toBe(false)
  })
})
