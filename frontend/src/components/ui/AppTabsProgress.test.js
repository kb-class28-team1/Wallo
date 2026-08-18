import { nextTick } from "vue"
import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"

import AppProgress from "./AppProgress.vue"
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

describe("AppProgress", () => {
  it("renders accessible progress values and the calculated percentage", () => {
    const wrapper = mount(AppProgress, {
      props: {
        value: 45,
        max: 60,
        label: "목표 달성률",
        showValue: true,
        variant: "success",
        size: "lg",
      },
    })

    const track = wrapper.get('[role="progressbar"]')

    expect(wrapper.text()).toContain("목표 달성률")
    expect(wrapper.text()).toContain("75%")
    expect(track.classes()).toContain("app-progress--success")
    expect(track.classes()).toContain("app-progress--lg")
    expect(track.attributes("aria-valuenow")).toBe("45")
    expect(wrapper.get(".app-progress__bar").attributes("style")).toContain("width: 75%")
  })
})
