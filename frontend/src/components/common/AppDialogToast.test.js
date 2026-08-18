import { mount } from "@vue/test-utils"
import { afterEach, describe, expect, it, vi } from "vitest"

import AppDialog from "./AppDialog.vue"
import AppToast from "./AppToast.vue"

afterEach(() => {
  vi.useRealTimers()
})

describe("AppDialog", () => {
  it("keeps the confirm and cancel interaction contract", async () => {
    const wrapper = mount(AppDialog, {
      props: {
        visible: true,
        title: "삭제 확인",
        message: "정말 삭제하시겠습니까?",
        showCancel: true,
      },
    })

    expect(wrapper.get('[role="dialog"]').text()).toContain("정말 삭제하시겠습니까?")
    expect(wrapper.get("[data-modal-confirm]").text()).toBe("확인")
    expect(wrapper.get(".app-dialog-cancel").text()).toBe("취소")

    await wrapper.get("[data-modal-confirm]").trigger("click")
    await wrapper.get(".app-dialog-cancel").trigger("click")

    expect(wrapper.emitted("confirm")).toHaveLength(1)
    expect(wrapper.emitted("close")).toHaveLength(1)
  })
})

describe("AppToast", () => {
  it("closes manually and automatically after its duration", async () => {
    vi.useFakeTimers()

    const wrapper = mount(AppToast, {
      props: {
        visible: true,
        message: "동기화가 완료되었습니다.",
        placement: "bottom-end",
      },
    })

    expect(wrapper.find(".app-toast--bottom-end").exists()).toBe(true)
    expect(wrapper.text()).toContain("동기화가 완료되었습니다.")

    await wrapper.get(".app-alert__close").trigger("click")
    expect(wrapper.emitted("close")).toHaveLength(1)

    const autoCloseWrapper = mount(AppToast, {
      props: {
        visible: true,
        message: "자동 닫힘",
        duration: 1000,
      },
    })

    vi.advanceTimersByTime(1000)

    expect(wrapper.emitted("close")).toHaveLength(1)
    expect(autoCloseWrapper.emitted("close")).toHaveLength(1)
  })
})
