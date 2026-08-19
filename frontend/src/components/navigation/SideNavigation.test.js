import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import SideNavigation from "./SideNavigation.vue"
import { getCurrentChallenge } from "@/api/challengeApi"

const mocks = vi.hoisted(() => ({
  route: {
    path: "/my-feeds",
    name: "my-feeds",
  },
  routerPush: vi.fn(),
}))

vi.mock("vue-router", () => ({
  RouterLink: {
    props: ["to"],
    template: '<a :href="to"><slot /></a>',
  },
  useRoute: () => mocks.route,
  useRouter: () => ({
    push: mocks.routerPush,
  }),
}))

vi.mock("@/api/challengeApi", () => ({
  getCurrentChallenge: vi.fn(),
}))

describe("SideNavigation", () => {
  beforeEach(() => {
    mocks.route.path = "/my-feeds"
    mocks.route.name = "my-feeds"
    mocks.routerPush.mockReset()
    getCurrentChallenge.mockResolvedValue({ joined: true, id: 7 })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it("keeps challenge navigation open and routes to my feeds", async () => {
    const wrapper = mount(SideNavigation, {
      global: {
        stubs: {
          AppDialog: { template: "<div />" },
        },
      },
    })

    await flushPromises()

    expect(wrapper.find(".challenge-group").classes()).toContain("challenge-group-active")
    expect(wrapper.find("#challenge-submenu").exists()).toBe(true)
    expect(wrapper.findAll(".submenu-item")).toHaveLength(4)
    expect(wrapper.find(".submenu-link-active").text()).toContain("내 게시물")
    expect(wrapper.find(".sidebar-card").classes()).toContain("app-card")

    const myFeedsButton = wrapper
      .findAll(".submenu-item")
      .find((item) => item.text().includes("내 게시물"))
    await myFeedsButton.trigger("click")
    await flushPromises()

    expect(getCurrentChallenge).toHaveBeenCalledOnce()
    expect(mocks.routerPush).toHaveBeenCalledWith("/my-feeds")

    wrapper.unmount()
  })

  it("adds a collapsible monthly report menu under asset management", async () => {
    mocks.route.path = "/dashboard"
    mocks.route.name = "dashboard"

    const wrapper = mount(SideNavigation, {
      global: {
        stubs: {
          AppDialog: { template: "<div />" },
        },
      },
    })

    expect(wrapper.find(".asset-group").exists()).toBe(true)
    expect(wrapper.find("#asset-submenu").exists()).toBe(false)
    expect(wrapper.find(".asset-collapse-toggle .bi-chevron-down").exists()).toBe(true)
    expect(wrapper.find(".asset-collapse-toggle").attributes("aria-expanded")).toBe("false")

    await wrapper.find(".asset-collapse-toggle").trigger("click")

    expect(wrapper.find("#asset-submenu").exists()).toBe(true)
    expect(wrapper.find(".asset-monthly-report").text()).toContain("월별 리포트")

    await wrapper.find(".asset-collapse-toggle").trigger("click")

    expect(wrapper.find("#asset-submenu").exists()).toBe(false)

    wrapper.unmount()
  })

  it("keeps the asset submenu open and highlights monthly reports on its route", async () => {
    mocks.route.path = "/assets/expenses"
    mocks.route.name = "expenses"

    const wrapper = mount(SideNavigation, {
      global: {
        stubs: {
          AppDialog: { template: "<div />" },
        },
      },
    })

    await flushPromises()

    expect(wrapper.find(".asset-group").classes()).toContain("asset-group-active")
    expect(wrapper.find("#asset-submenu").exists()).toBe(true)
    expect(wrapper.find(".asset-monthly-report").classes()).toContain("submenu-link-active")
    expect(wrapper.find(".asset-collapse-toggle").attributes("aria-expanded")).toBe("true")

    wrapper.unmount()
  })
})
