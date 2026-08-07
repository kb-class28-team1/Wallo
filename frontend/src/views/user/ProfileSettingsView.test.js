import { ref } from "vue"
import { createMemoryHistory, createRouter } from "vue-router"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { flushPromises, mount } from "@vue/test-utils"
import ProfileSettingsView from "./ProfileSettingsView.vue"
import { useUserStore } from "@/stores/userStore"

vi.mock("@/stores/userStore", () => ({
  useUserStore: vi.fn(),
}))

const createTestRouter = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { name: "user-profile", path: "/settings/profile", component: { template: "<div />" } },
      { name: "login", path: "/login", component: { template: "<div />" } },
    ],
  })

  await router.push({ name: "user-profile" })
  await router.isReady()
  return router
}

describe("ProfileSettingsView", () => {
  let wrapper
  let store
  let user

  beforeEach(() => {
    user = ref({
      id: 7,
      name: "김혜진",
      nickname: "저축왕 펭귄",
      email: "penguin@example.com",
      profileImageUrl: "/images/profiles/default-profile.svg",
    })

    store = {
      user,
      profileImageUrl: ref(user.value.profileImageUrl),
      fetchProfile: vi.fn().mockResolvedValue(user.value),
      updateNickname: vi.fn(async (nickname) => {
        user.value = { ...user.value, nickname }
        return nickname
      }),
      updateProfileImage: vi.fn(),
      resetProfileImage: vi.fn(),
      useDefaultProfileImage: vi.fn(),
      clearAuth: vi.fn(),
    }
    useUserStore.mockReturnValue(store)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it("loads the profile and keeps name and email read-only", async () => {
    const router = await createTestRouter()
    wrapper = mount(ProfileSettingsView, { global: { plugins: [router] } })
    await flushPromises()

    expect(store.fetchProfile).toHaveBeenCalledOnce()
    expect(wrapper.find("#profile-name").element.disabled).toBe(true)
    expect(wrapper.find("#profile-email").element.disabled).toBe(true)
    expect(wrapper.find("#profile-name").element.value).toBe("김혜진")
    expect(wrapper.find("#profile-email").element.value).toBe("penguin@example.com")
  })

  it("updates only the editable nickname", async () => {
    const router = await createTestRouter()
    wrapper = mount(ProfileSettingsView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.find("#profile-nickname").setValue("새로운 펭귄")
    await wrapper.find("form").trigger("submit")
    await flushPromises()

    expect(store.updateNickname).toHaveBeenCalledWith("새로운 펭귄")
    expect(wrapper.text()).toContain("닉네임이 저장되었습니다.")
  })
})
