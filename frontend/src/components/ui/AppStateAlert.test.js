import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"

import AppAlert from "./AppAlert.vue"
import AppState from "./AppState.vue"

describe("AppState", () => {
  it("renders an error state and emits its action", async () => {
    const wrapper = mount(AppState, {
      props: {
        type: "error",
        actionText: "다시 시도",
      },
    })

    expect(wrapper.attributes("role")).toBe("alert")
    expect(wrapper.text()).toContain("문제가 발생했습니다")
    expect(wrapper.text()).toContain("다시 시도")

    await wrapper.get("button").trigger("click")

    expect(wrapper.emitted("action")).toHaveLength(1)
  })
})

describe("AppAlert", () => {
  it("renders semantic content and emits close for a dismissible alert", async () => {
    const wrapper = mount(AppAlert, {
      props: {
        variant: "success",
        title: "저장 완료",
        dismissible: true,
      },
      slots: {
        default: "변경사항이 저장되었습니다.",
      },
    })

    expect(wrapper.classes()).toContain("app-alert--success")
    expect(wrapper.attributes("role")).toBe("alert")
    expect(wrapper.text()).toContain("저장 완료")
    expect(wrapper.text()).toContain("변경사항이 저장되었습니다.")

    await wrapper.get(".app-alert__close").trigger("click")

    expect(wrapper.emitted("close")).toHaveLength(1)
  })
})
