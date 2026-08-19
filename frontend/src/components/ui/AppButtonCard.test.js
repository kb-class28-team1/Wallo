import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"

import AppButton from "./AppButton.vue"
import AppCard from "./AppCard.vue"

describe("AppButton", () => {
  it("renders the selected variant and size and emits click", async () => {
    const wrapper = mount(AppButton, {
      props: {
        variant: "danger",
        size: "lg",
      },
      slots: {
        default: "삭제",
      },
    })

    const button = wrapper.get("button")

    expect(button.classes()).toContain("app-button--danger")
    expect(button.classes()).toContain("app-button--lg")
    expect(button.text()).toBe("삭제")

    await button.trigger("click")

    expect(wrapper.emitted("click")).toHaveLength(1)
  })

  it("disables interaction while loading", async () => {
    const wrapper = mount(AppButton, {
      props: {
        loading: true,
      },
    })

    const button = wrapper.get("button")

    expect(button.attributes("disabled")).toBeDefined()
    expect(button.attributes("aria-busy")).toBe("true")

    await button.trigger("click")

    expect(wrapper.emitted("click")).toBeUndefined()
  })
})

describe("AppCard", () => {
  it("renders header, body, and footer slots with the selected style", () => {
    const wrapper = mount(AppCard, {
      props: {
        variant: "accent",
        padding: "lg",
        interactive: true,
      },
      slots: {
        header: "요약",
        default: "카드 내용",
        footer: "더 보기",
      },
    })

    expect(wrapper.classes()).toContain("app-card--accent")
    expect(wrapper.classes()).toContain("app-card--padding-lg")
    expect(wrapper.classes()).toContain("app-card--interactive")
    expect(wrapper.find(".app-card__header").text()).toBe("요약")
    expect(wrapper.find(".app-card__body").text()).toBe("카드 내용")
    expect(wrapper.find(".app-card__footer").text()).toBe("더 보기")
  })
})
