import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import httpClient from "@/api/httpClient"
import AuthenticatedImage from "./AuthenticatedImage.vue"

vi.mock("@/api/httpClient", () => ({
  default: {
    get: vi.fn(),
  },
}))

describe("AuthenticatedImage", () => {
  const originalCreateObjectUrl = URL.createObjectURL
  const originalRevokeObjectUrl = URL.revokeObjectURL

  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => "blob:profile-image")
    URL.revokeObjectURL = vi.fn()
    httpClient.get.mockReset()
  })

  afterEach(() => {
    URL.createObjectURL = originalCreateObjectUrl
    URL.revokeObjectURL = originalRevokeObjectUrl
  })

  it("loads protected profile images through the authenticated HTTP client", async () => {
    httpClient.get.mockResolvedValue({ data: new Blob(["image"], { type: "image/png" }) })

    const wrapper = mount(AuthenticatedImage, {
      props: { src: "/api/profile-images/profile.png", alt: "프로필" },
    })
    await flushPromises()

    expect(httpClient.get).toHaveBeenCalledWith("/api/profile-images/profile.png", {
      responseType: "blob",
    })
    expect(wrapper.find("img").attributes("src")).toBe("blob:profile-image")
  })

  it("keeps public images direct and falls back when the protected request fails", async () => {
    httpClient.get.mockRejectedValue(new Error("Unauthorized"))

    const publicWrapper = mount(AuthenticatedImage, {
      props: { src: "/images/profiles/default-profile.svg" },
    })
    expect(publicWrapper.find("img").attributes("src")).toBe(
      "/images/profiles/default-profile.svg",
    )
    expect(httpClient.get).not.toHaveBeenCalled()

    const protectedWrapper = mount(AuthenticatedImage, {
      props: { src: "/api/profile-images/profile.png" },
    })
    await flushPromises()

    expect(protectedWrapper.find("img").attributes("src")).toBe(
      "/images/profiles/default-profile.svg",
    )
  })
})
