import { createMemoryHistory, createRouter } from "vue-router"
import { afterEach, describe, expect, it } from "vitest"
import { mount } from "@vue/test-utils"
import SettingsView from "./SettingsView.vue"

const StubView = { template: "<div />" }

const createTestRouter = async (initialRoute) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: "/settings",
        component: StubView,
        children: [
          { name: "user-profile", path: "profile", component: StubView },
          { name: "connection-management", path: "connections", component: StubView },
          { name: "password-settings", path: "password", component: StubView },
        ],
      },
    ],
  })

  await router.push({ name: initialRoute })
  await router.isReady()
  return router
}

describe("SettingsView", () => {
  let wrapper

  afterEach(() => {
    wrapper?.unmount()
  })

  it("marks the current settings tab as the active page", async () => {
    const router = await createTestRouter("user-profile")
    wrapper = mount(SettingsView, { global: { plugins: [router] } })

    expect(wrapper.find(".app-page-header").exists()).toBe(true)
    expect(wrapper.find(".app-page-header__title").text()).toBe("설정")
    expect(wrapper.find(".app-page-header__description").text()).toContain("프로필과 연결된 자산")
    expect(wrapper.find(".settings-navigation").classes()).toContain("app-card")
    expect(wrapper.find("nav[aria-label='설정 메뉴']").exists()).toBe(true)

    const tabs = wrapper.findAll(".settings-tab")
    expect(tabs).toHaveLength(3)
    expect(tabs.map((tab) => tab.attributes("href"))).toEqual([
      "/settings/profile",
      "/settings/connections",
      "/settings/password",
    ])
    expect(tabs[0].attributes("aria-current")).toBe("page")
    expect(tabs[1].attributes("aria-current")).toBeUndefined()

    await router.push({ name: "password-settings" })

    expect(tabs[2].attributes("aria-current")).toBe("page")
    expect(tabs[0].attributes("aria-current")).toBeUndefined()
  })
})
