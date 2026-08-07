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

    expect(store.changePassword).toHaveBeenCalledWith({
      currentPassword: "wrong-password",
      newPassword: "new-password-123",
      newPasswordConfirm: "new-password-123",
    })
    expect(wrapper.find('[role="alert"]').text()).toContain("현재 비밀번호가 올바르지 않습니다.")
  })
})
