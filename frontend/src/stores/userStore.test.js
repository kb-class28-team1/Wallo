import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { getProfile } from "@/api/userApi"
import { useUserStore } from "./userStore"

vi.mock("@/api/authApi", () => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

vi.mock("@/api/userApi", () => ({
  changePassword: vi.fn(),
  getProfile: vi.fn(),
  resetProfileImage: vi.fn(),
  updateNickname: vi.fn(),
  updateProfileImage: vi.fn(),
}))

describe("userStore profile loading", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it("shares one in-flight profile request between callers", async () => {
    let resolveProfile
    getProfile.mockReturnValue(new Promise((resolve) => {
      resolveProfile = resolve
    }))

    const userStore = useUserStore()
    const firstRequest = userStore.fetchProfile()
    const secondRequest = userStore.fetchProfile()

    expect(getProfile).toHaveBeenCalledOnce()

    const profile = {
      id: 7,
      name: "김혜진",
      nickname: "저축왕 펭귄",
      email: "user@wallo.test",
      profileImageUrl: "/images/profiles/default-profile.svg",
    }
    resolveProfile(profile)

    await expect(Promise.all([firstRequest, secondRequest])).resolves.toEqual([
      profile,
      profile,
    ])
    expect(userStore.user.nickname).toBe("저축왕 펭귄")
  })
})
