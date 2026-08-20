import { mount } from "@vue/test-utils"
import { afterEach, describe, expect, it, vi } from "vitest"

import OnboardingWelcomeView from "./OnboardingWelcomeView.vue"

const router = {
  push: vi.fn().mockResolvedValue(undefined),
}

vi.mock("vue-router", () => ({
  useRouter: () => router,
}))

describe("OnboardingWelcomeView", () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it("introduces the main features and moves to asset connection after the last step", async () => {
    const wrapper = mount(OnboardingWelcomeView)

    expect(wrapper.get(".welcome-penguin").attributes("src")).toBe(
      "/images/onboarding/welcome-penguin.png",
    )
    expect(wrapper.get("h1").text()).toContain("안녕하세요, 왈로예요!")
    expect(wrapper.get(".onboarding-progress").attributes("aria-label")).toContain("1단계")

    await wrapper.get(".next-button").trigger("click")
    expect(wrapper.text()).toContain("흩어진 자산을 한 번에 연결해요")
    expect(router.push).not.toHaveBeenCalled()

    await wrapper.get(".next-button").trigger("click")
    expect(wrapper.text()).toContain("내 금융 생활을 한눈에 확인해요")

    await wrapper.get(".next-button").trigger("click")
    expect(wrapper.text()).toContain("AI가 나에게 필요한 답을 찾아줘요")

    await wrapper.get(".next-button").trigger("click")
    expect(wrapper.text()).toContain("어려운 금융 뉴스를 쉽게 읽어요")
    expect(wrapper.text()).toContain("금융 용어 설명")

    await wrapper.get(".next-button").trigger("click")
    expect(wrapper.text()).toContain("함께 절약하고 보상도 받아요")
    expect(wrapper.get(".next-button").text()).toContain("자산 연동 시작하기")

    await wrapper.get(".next-button").trigger("click")

    expect(router.push).toHaveBeenCalledWith({ name: "connection" })
    wrapper.unmount()
  })
})
