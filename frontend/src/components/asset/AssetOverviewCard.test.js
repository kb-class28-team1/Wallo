import { describe, expect, it, vi } from "vitest"
import { mount } from "@vue/test-utils"
import AssetOverviewCard from "./AssetOverviewCard.vue"

vi.mock("vue-chartjs", () => ({
  Doughnut: {
    template: '<div data-testid="asset-doughnut" />',
  },
}))

const globalStubs = {
  RouterLink: {
    props: ["to"],
    template: '<a :href="to"><slot /></a>',
  },
}

describe("AssetOverviewCard", () => {
  it("maps the CHECKING asset code to the Korean 입출금 label", () => {
    const wrapper = mount(AssetOverviewCard, {
      props: {
        assets: {
          totalAssets: 1_650_000,
          assetCategoryBreakdown: [{ category: "CHECKING", amount: 1_650_000 }],
        },
      },
      global: { stubs: globalStubs },
    })

    expect(wrapper.text()).toContain("입출금")
    expect(wrapper.text()).not.toContain("CHECKING")
  })

  it("shows the unified card summary and category composition", () => {
    const wrapper = mount(AssetOverviewCard, {
      props: {
        assets: {
          totalAssets: 2_000_000,
          assetCategoryBreakdown: [
            { category: "DEPOSIT", amount: 1_500_000 },
            { category: "LOAN", amount: -500_000 },
            { category: "STOCK", amount: 500_000 },
          ],
        },
      },
      global: { stubs: globalStubs },
    })

    expect(wrapper.find(".asset-overview-card").classes()).toContain("app-card")
    expect(wrapper.find('[data-testid="asset-doughnut"]').exists()).toBe(true)
    expect(wrapper.get(".connection-management-button").classes()).toContain("app-action-link")
    expect(wrapper.get(".connection-management-button").attributes("href")).toBe(
      "/users/profile/connections",
    )
    expect(wrapper.text()).toContain("자산 한눈에 보기")
    expect(wrapper.text()).toContain("2,500,000원")
    expect(wrapper.text()).toContain("순자산")
    expect(wrapper.text()).toContain("2,000,000원")
    expect(wrapper.text()).toContain("대출(부채)")
    expect(wrapper.text()).toContain("500,000원")
    expect(wrapper.findAll(".asset-balance-summary > div")).toHaveLength(2)
    expect(wrapper.text()).toContain("입출금")
    expect(wrapper.text()).toContain("투자")
    expect(wrapper.text()).toContain("60%")
    expect(wrapper.text()).toContain("20%")
  })

  it("shows the shared empty state when no category data is available", () => {
    const wrapper = mount(AssetOverviewCard, {
      props: {
        assets: {
          totalAssets: 0,
          assetCategoryBreakdown: [],
        },
      },
      global: { stubs: globalStubs },
    })

    expect(wrapper.find(".asset-empty-state").classes()).toContain("app-state")
    expect(wrapper.find(".asset-empty-state").classes()).toContain("app-state--empty")
    expect(wrapper.find('[data-testid="asset-doughnut"]').exists()).toBe(false)
    expect(wrapper.text()).toContain("카테고리별 자산 데이터가 없습니다.")
    expect(wrapper.text()).toContain("자산을 연동하면 카테고리별 금액과 비율을 확인할 수 있습니다.")
  })
})
