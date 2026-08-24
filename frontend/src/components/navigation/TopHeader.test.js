import { flushPromises, mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import TopHeader from "./TopHeader.vue"
import { getTodayMissions } from "@/api/missionApi"
import { useMissionStore } from "@/stores/missionStore"
import { useUserStore } from "@/stores/userStore"
import { useToastStore } from "@/stores/toastStore"

const router = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
}))

let pinia

vi.mock("vue-router", () => ({
  RouterLink: { template: "<a><slot /></a>" },
  useRouter: () => router,
}))

vi.mock("@/api/missionApi", () => ({
  completeSelfCheckMission: vi.fn(),
  generateNextDayMissions: vi.fn(),
  getTodayMissions: vi.fn(),
  verifyMissionWithFeed: vi.fn(),
  verifyTransactionMission: vi.fn(),
}))

const mountTopHeader = () => {
  useMissionStore().startLifecycle()
  return mount(TopHeader, {
    global: {
      plugins: [pinia],
      stubs: {
        AuthenticatedImage: { template: "<img />" },
      },
    },
  })
}

describe("TopHeader", () => {
  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    vi.clearAllMocks()
    vi.stubGlobal("alert", vi.fn())
    vi.spyOn(useUserStore(), "fetchUserProfile").mockResolvedValue(null)
    getTodayMissions.mockResolvedValue({
      date: "2026-08-17",
      status: "WAITING_ANALYSIS",
      missions: [],
    })
  })

  afterEach(() => {
    useMissionStore().stopLifecycle()
    useMissionStore().reset()
    vi.unstubAllGlobals()
  })

  it("does not alert when today's missions are waiting for analysis", async () => {
    const wrapper = mountTopHeader()
    await flushPromises()

    expect(globalThis.alert).not.toHaveBeenCalled()

    await wrapper.find(".mission-trigger").trigger("click")
    expect(wrapper.text()).toContain("소비 분석이 완료되면 오늘의 미션이 생성됩니다.")
    expect(wrapper.find(".mission-empty button").exists()).toBe(true)

    wrapper.unmount()
  })

  it("shows a toast when background mission generation is rate limited", async () => {
    getTodayMissions.mockResolvedValue({
      date: "2026-08-17",
      status: "GENERATION_FAILED",
      failureReason: "RATE_LIMIT",
      missions: [],
    })

    const wrapper = mountTopHeader()
    await flushPromises()

    expect(useToastStore().toasts[0].message).toBe(
      "AI 사용량 제한으로 잠시 후 다시 생성됩니다.",
    )
    expect(globalThis.alert).not.toHaveBeenCalled()

    wrapper.unmount()
  })
})
