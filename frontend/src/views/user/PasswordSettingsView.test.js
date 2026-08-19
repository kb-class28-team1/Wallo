import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { flushPromises, mount } from "@vue/test-utils"
import PasswordSettingsView from "./PasswordSettingsView.vue"
import { useUserStore } from "@/stores/userStore"

vi.mock("@/stores/userStore", () => ({
  useUserStore: vi.fn(),
}))

describe("PasswordSettingsView", () => {
  let wrapper
  let store

  beforeEach(() => {
    store = {
      changePassword: vi.fn().mockRejectedValue(new Error("현재 비밀번호가 올바르지 않습니다.")),
    }
    useUserStore.mockReturnValue(store)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it("shows a validation error without redirecting when the current password is wrong", async () => {
    wrapper = mount(PasswordSettingsView)

    await wrapper.find("#current-password").setValue("wrong-password")
    await wrapper.find("#new-password").setValue("new-password-123")
    await wrapper.find("#new-password-confirm").setValue("new-password-123")
    await wrapper.find("form").trigger("submit")
    await flushPromises()

    expect(wrapper.find(".settings-panel").classes()).toContain("app-card")
    expect(wrapper.findAll(".app-form-field")).toHaveLength(3)
    expect(wrapper.find(".password-submit").classes()).toContain("app-button")
    expect(store.changePassword).toHaveBeenCalledWith({
      currentPassword: "wrong-password",
      newPassword: "new-password-123",
      newPasswordConfirm: "new-password-123",
    })
    expect(wrapper.find('[role="alert"]').text()).toContain("현재 비밀번호가 올바르지 않습니다.")
  })

  it("shows a validation alert without calling the password API", async () => {
    wrapper = mount(PasswordSettingsView)

    await wrapper.find("#current-password").setValue("current-password")
    await wrapper.find("#new-password").setValue("short")
    await wrapper.find("#new-password-confirm").setValue("short")
    await wrapper.find("form").trigger("submit")

    expect(store.changePassword).not.toHaveBeenCalled()
    expect(wrapper.find(".password-message").classes()).toContain("app-alert--danger")
    expect(wrapper.find('[role="alert"]').text()).toContain("8자 이상 72자 이하")
  })

  it("clears the form and shows a success alert after changing the password", async () => {
    store.changePassword.mockResolvedValueOnce({})
    wrapper = mount(PasswordSettingsView)

    await wrapper.find("#current-password").setValue("current-password")
    await wrapper.find("#new-password").setValue("new-password-123")
    await wrapper.find("#new-password-confirm").setValue("new-password-123")
    await wrapper.find("form").trigger("submit")
    await flushPromises()

    expect(wrapper.find(".password-message").classes()).toContain("app-alert--success")
    expect(wrapper.find('[role="status"]').text()).toContain("비밀번호가 변경되었습니다.")
    expect(wrapper.find("#current-password").element.value).toBe("")
    expect(wrapper.find("#new-password").element.value).toBe("")
    expect(wrapper.find("#new-password-confirm").element.value).toBe("")
  })
})
