import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { nextTick, reactive } from "vue"

import SideNavigation from "./SideNavigation.vue"
import { getCurrentChallenge } from "@/api/challengeApi"

const mocks = vi.hoisted(() => ({
  route: {
    path: "/my-feeds",
    name: "my-feeds",
  },
  routerPush: vi.fn(),
  routerReplace: vi.fn(),
  logout: vi.fn(),
}))

vi.mock("vue-router", () => ({
  RouterLink: {
    props: ["to"],
    template: '<a :href="to"><slot /></a>',
  },
  useRoute: () => mocks.route,
  useRouter: () => ({
    push: mocks.routerPush,
    replace: mocks.routerReplace,
  }),
}))

vi.mock("@/stores/userStore", () => ({
  useUserStore: () => ({
    logout: mocks.logout,
  }),
}))

vi.mock("@/api/challengeApi", () => ({
  getCurrentChallenge: vi.fn(),
}))

describe("SideNavigation", () => {
  beforeEach(() => {
    mocks.route = reactive({
      path: "/my-feeds",
      name: "my-feeds",
    })
    mocks.routerPush.mockReset()
    mocks.routerReplace.mockReset()
    mocks.logout.mockReset()
    mocks.logout.mockResolvedValue(undefined)
    getCurrentChallenge.mockResolvedValue({ joined: true, id: 7 })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it("shows the Wallo wordmark without a brand character image", () => {
    const wrapper = mount(SideNavigation, {
      global: {
        stubs: {
          AppDialog: { template: "<div />" },
        },
      },
    })

    expect(wrapper.find(".brand-name").text()).toBe("Wallo")
    expect(wrapper.find(".brand img").exists()).toBe(false)
    expect(wrapper.find(".brand").attributes("aria-label")).toBe("Wallo 대시보드로 이동")

    wrapper.unmount()
  })

  it("places settings and logout above the sidebar character card", async () => {
    const wrapper = mount(SideNavigation, {
      global: {
        stubs: {
          AppDialog: { template: "<div />" },
        },
      },
    })

    const footer = wrapper.find(".sidebar-footer")
    expect(footer.find(".sidebar-footer-divider").exists()).toBe(true)
    expect(footer.text()).toContain("설정")
    expect(footer.text()).toContain("로그아웃")
    expect(footer.find(".sidebar-card").exists()).toBe(true)
    expect(wrapper.find(".utility-group").text()).not.toContain("설정")
    expect(wrapper.findAll('a[href="/users/profile"]')).toHaveLength(1)
    expect(wrapper.find('a[href="/assets"] .bi-bar-chart-line').exists()).toBe(true)
    expect(wrapper.find('a[href="/point-shop"] .bi-gift').exists()).toBe(true)

    await footer.find(".sidebar-logout").trigger("click")
    await flushPromises()

    expect(mocks.logout).toHaveBeenCalledOnce()
    expect(mocks.routerReplace).toHaveBeenCalledWith("/login")

    wrapper.unmount()
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
    expect(wrapper.findAll(".submenu-item")).toHaveLength(3)
    expect(wrapper.text()).not.toContain("피드 목록")
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

  it("routes to the challenge feed when clicking the challenge title", async () => {
    mocks.route.path = "/dashboard"
    mocks.route.name = "dashboard"

    const wrapper = mount(SideNavigation, {
      global: {
        stubs: {
          AppDialog: { template: "<div />" },
        },
      },
    })

    await wrapper.find(".challenge-title").trigger("click")
    await flushPromises()

    expect(getCurrentChallenge).toHaveBeenCalledOnce()
    expect(mocks.routerPush).toHaveBeenCalledWith("/challenges/7/feeds")
    expect(wrapper.text()).not.toContain("피드 목록")

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
    expect(wrapper.find('a[href="/assets/categories"]').text()).toContain("카테고리별 소비")

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

  it("opens on asset navigation and closes when leaving asset pages", async () => {
    mocks.route.path = "/assets/categories"
    mocks.route.name = "category-expenses"

    const wrapper = mount(SideNavigation, {
      global: {
        stubs: {
          AppDialog: { template: "<div />" },
        },
      },
    })

    await flushPromises()

    expect(wrapper.find("#asset-submenu").exists()).toBe(true)
    expect(wrapper.find('a[href="/assets/categories"]').classes()).toContain("submenu-link-active")
    expect(wrapper.find(".asset-collapse-toggle").attributes("aria-expanded")).toBe("true")

    mocks.route.path = "/dashboard"
    mocks.route.name = "dashboard"
    await nextTick()
    await flushPromises()

    expect(wrapper.find(".asset-collapse-toggle").attributes("aria-expanded")).toBe("false")
    expect(wrapper.find("#asset-submenu").exists()).toBe(false)

    wrapper.unmount()
  })

  it("opens on challenge navigation and closes when leaving challenge pages", async () => {
    mocks.route.path = "/challenges/current"
    mocks.route.name = "current-challenge"

    const wrapper = mount(SideNavigation, {
      global: {
        stubs: {
          AppDialog: { template: "<div />" },
        },
      },
    })

    await flushPromises()

    expect(wrapper.find("#challenge-submenu").exists()).toBe(true)
    expect(wrapper.find(".challenge-group .collapse-toggle").attributes("aria-expanded")).toBe("true")

    mocks.route.path = "/dashboard"
    mocks.route.name = "dashboard"
    await nextTick()
    await flushPromises()

    expect(wrapper.find(".challenge-group .collapse-toggle").attributes("aria-expanded")).toBe("false")
    expect(wrapper.find("#challenge-submenu").exists()).toBe(false)

    wrapper.unmount()
  })
})
