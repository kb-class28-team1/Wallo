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
})
