import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import LoginView from "./LoginView.vue"
import SignupView from "./SignupView.vue"
import ConnectionView from "@/views/asset/ConnectionView.vue"
import { signup } from "@/api/authApi"

const mocks = vi.hoisted(() => ({
  route: { query: {} },
  router: {
    replace: vi.fn(),
    push: vi.fn(),
  },
  userStore: {
    isLoading: false,
    login: vi.fn(),
    restoreSession: vi.fn(),
  },
  reportStore: {
    setAnnualSalaryLookupStatus: vi.fn(),
  },
  assetStore: {
    invalidateAssetsCache: vi.fn(),
  },
}))

vi.mock("vue-router", () => ({
  RouterLink: {
    props: ["to"],
    template: "<a :href=\"typeof to === 'string' ? to : undefined\"><slot /></a>",
  },
  useRoute: () => mocks.route,
  useRouter: () => mocks.router,
}))

vi.mock("@/api/authApi", () => ({
  signup: vi.fn(),
}))

vi.mock("@/api/assetApi", () => ({
  connectAllAssets: vi.fn(),
}))

vi.mock("@/api/connectionApi", () => ({
  invalidateConnectionsCache: vi.fn(),
}))

vi.mock("@/stores/userStore", () => ({
  useUserStore: () => mocks.userStore,
}))

vi.mock("@/stores/assetReportStore", () => ({
  useReportStore: () => mocks.reportStore,
}))

vi.mock("@/stores/assetStore", () => ({
  useAssetStore: () => mocks.assetStore,
}))

const routerLinkStub = {
  RouterLink: {
    props: ["to"],
    template: "<a :href=\"typeof to === 'string' ? to : undefined\"><slot /></a>",
  },
}

describe("auth and asset connection views", () => {
  let wrapper

  beforeEach(() => {
    mocks.route.query = {}
    mocks.userStore.isLoading = false
    mocks.userStore.login.mockReset().mockResolvedValue({ connectionCompleted: true })
    mocks.userStore.restoreSession.mockReset().mockResolvedValue({})
    mocks.router.replace.mockReset().mockResolvedValue(undefined)
    mocks.router.push.mockReset().mockResolvedValue(undefined)
    mocks.reportStore.setAnnualSalaryLookupStatus.mockReset()
    mocks.assetStore.invalidateAssetsCache.mockReset()
    signup.mockReset().mockResolvedValue({})
  })

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ""
    vi.clearAllMocks()
  })

  it("uses shared card, alert, form field, and button primitives on login", async () => {
    mocks.route.query = { reason: "expired" }
    wrapper = mount(LoginView, { global: { stubs: routerLinkStub } })

    expect(wrapper.find(".auth-card").classes()).toContain("app-card")
    expect(wrapper.find(".session-message").classes()).toContain("app-alert")
    expect(wrapper.findAll(".app-form-field")).toHaveLength(2)
    expect(wrapper.find(".login-submit").classes()).toContain("app-button")

    await wrapper.get("form").trigger("submit")

    expect(wrapper.get("#login-email-message").text()).toContain("이메일을 입력해 주세요.")
    expect(wrapper.get("#login-password-message").text()).toContain("비밀번호를 입력해 주세요.")
    expect(mocks.userStore.login).not.toHaveBeenCalled()
  })

  it("shows the shared success dialog after signup and keeps the login redirect", async () => {
    wrapper = mount(SignupView, { global: { stubs: routerLinkStub } })

    await wrapper.get("#signup-name").setValue("홍길동")
    await wrapper.get("#signup-nickname").setValue("절약왕")
    await wrapper.get("#signup-email").setValue("test@wallo.com")
    await wrapper.get("#signup-password").setValue("password123")
    await wrapper.get("#signup-password-confirm").setValue("password123")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(signup).toHaveBeenCalledWith({
      name: "홍길동",
      nickname: "절약왕",
      email: "test@wallo.com",
      password: "password123",
    })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.find('[role="dialog"]').text()).toContain("회원가입이 완료되었어요!")
    expect(wrapper.find(".app-dialog-confirm").classes()).toContain("app-button")

    await wrapper.get(".app-dialog-confirm").trigger("click")
    await flushPromises()

    expect(mocks.router.replace).toHaveBeenCalledWith({
      name: "login",
      query: { redirect: "/connections/mydata" },
    })
  })

  it("uses shared fields and button state on the first asset connection step", async () => {
    wrapper = mount(ConnectionView)

    expect(wrapper.find(".connection-form-panel").classes()).toContain("app-card")
    expect(wrapper.findAll(".connection-form-fields .app-form-field")).toHaveLength(2)
    expect(wrapper.find(".connect-action").classes()).toContain("app-button")
    expect(wrapper.get(".connect-action").attributes("disabled")).toBeDefined()

    await wrapper.get("#creditConsent").setValue(true)
    await wrapper.get("#userName").setValue("홍길동")
    await wrapper.get("#phoneNumber").setValue("01012345678")

    expect(wrapper.get(".connect-action").attributes("disabled")).toBeUndefined()
  })
})
