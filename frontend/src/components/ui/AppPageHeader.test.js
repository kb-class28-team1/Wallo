import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"

import AppPageHeader from "./AppPageHeader.vue"

describe("AppPageHeader", () => {
  it("renders the shared title id and title slot", () => {
    const wrapper = mount(AppPageHeader, {
      props: {
        title: "기본 제목",
        titleId: "page-title",
      },
      slots: {
        title: "강조 제목",
      },
    })

    expect(wrapper.get(".app-page-header__title").attributes("id")).toBe("page-title")
    expect(wrapper.get(".app-page-header__title").text()).toBe("강조 제목")
  })
})
