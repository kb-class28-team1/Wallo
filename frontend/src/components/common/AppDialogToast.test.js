import { mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { afterEach, describe, expect, it, vi } from "vitest"
import { nextTick } from "vue"

import AppDialog from "./AppDialog.vue"
import AppToast from "./AppToast.vue"
import { useToastStore } from "@/stores/toastStore"

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
    const pinia = createPinia()
    setActivePinia(pinia)
    const toastStore = useToastStore()

    const wrapper = mount(AppToast, {
      global: { plugins: [pinia] },
    })
    toastStore.show("동기화가 완료되었습니다.", { duration: 0 })
    await nextTick()

    expect(wrapper.find(".wallo-toast").exists()).toBe(true)
    expect(wrapper.text()).toContain("동기화가 완료되었습니다.")

    await wrapper.get(".wallo-toast-close").trigger("click")
    expect(toastStore.toasts).toHaveLength(0)

    const autoCloseWrapper = mount(AppToast, {
      global: { plugins: [pinia] },
    })
    toastStore.show("자동 닫힘", { duration: 1000 })
    await nextTick()

    vi.advanceTimersByTime(1000)
    await nextTick()

    expect(toastStore.toasts).toHaveLength(0)
    expect(autoCloseWrapper.text()).not.toContain("자동 닫힘")
  })
})
