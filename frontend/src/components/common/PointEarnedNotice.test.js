import { mount } from "@vue/test-utils"
import { afterEach, describe, expect, it, vi } from "vitest"

import PointEarnedNotice from "./PointEarnedNotice.vue"

afterEach(() => {
  vi.useRealTimers()
})

describe("PointEarnedNotice", () => {
  it("shows the earned amount at the top and closes automatically", async () => {
    vi.useFakeTimers()
    const wrapper = mount(PointEarnedNotice)

    window.dispatchEvent(
      new CustomEvent("wallo:point-earned", {
        detail: { point: 1250 },
      }),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".point-earned-notice").exists()).toBe(true)
    expect(wrapper.text()).toContain("포인트 1,250P가 적립되었습니다.")

    vi.advanceTimersByTime(4200)
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".point-earned-notice").exists()).toBe(false)
  })
})
