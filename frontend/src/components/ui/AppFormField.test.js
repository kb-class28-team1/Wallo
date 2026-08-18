import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"

import AppFormField from "./AppFormField.vue"

describe("AppFormField", () => {
  it("connects its label and emits the v-model value", async () => {
    const wrapper = mount(AppFormField, {
      props: {
        id: "email",
        label: "이메일",
        modelValue: "",
        required: true,
        helpText: "로그인에 사용할 이메일입니다.",
      },
    })

    const input = wrapper.get("input")
    const messageId = input.attributes("aria-describedby")

    expect(wrapper.get("label").attributes("for")).toBe("email")
    expect(input.attributes("required")).toBeDefined()
    expect(wrapper.get(`#${messageId}`).text()).toContain("로그인에 사용할 이메일입니다.")

    await input.setValue("user@example.com")

    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual(["user@example.com"])
    expect(wrapper.emitted("input")).toHaveLength(1)
  })

  it("exposes error state and supports a multiline field", () => {
    const wrapper = mount(AppFormField, {
      props: {
        id: "description",
        label: "설명",
        multiline: true,
        rows: 6,
        error: "설명을 입력해 주세요.",
      },
    })

    const textarea = wrapper.get("textarea")

    expect(textarea.attributes("rows")).toBe("6")
    expect(textarea.attributes("aria-invalid")).toBe("true")
    expect(wrapper.classes()).toContain("app-form-field--error")
    expect(wrapper.find(".app-form-field__message").text()).toBe("설명을 입력해 주세요.")
  })
})
