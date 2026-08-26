import { nextTick } from "vue"
import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"

import AppTabs from "./AppTabs.vue"

describe("AppTabs", () => {
  it("selects tabs and skips disabled items during keyboard navigation", async () => {
    const wrapper = mount(AppTabs, {
      props: {
        items: [
          { value: "overview", label: "개요" },
          { value: "disabled", label: "비활성", disabled: true },
          { value: "activity", label: "활동" },
        ],
      },
      attachTo: document.body,
    })

    const tabs = wrapper.findAll('[role="tab"]')

    expect(tabs[0].attributes("aria-selected")).toBe("true")
    expect(tabs[1].attributes("disabled")).toBeDefined()

    await tabs[0].trigger("keydown", { key: "ArrowRight" })
    await nextTick()

    expect(tabs[2].attributes("aria-selected")).toBe("true")
    expect(document.activeElement).toBe(tabs[2].element)
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["activity"])

    wrapper.unmount()
  })
})
