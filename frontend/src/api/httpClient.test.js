import { afterEach, describe, expect, it, vi } from "vitest"
import axios from "axios"
import httpClient, { setUnauthorizedHandler } from "./httpClient"

describe("httpClient authentication errors", () => {
  let originalAdapter

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter
    setUnauthorizedHandler(null)
    vi.restoreAllMocks()
  })

  it("routes profile API 401 responses through the global unauthorized handler", async () => {
    originalAdapter = httpClient.defaults.adapter
    const unauthorizedHandler = vi.fn()
    const refreshRequest = vi
      .spyOn(axios, "post")
      .mockRejectedValue(new Error("refresh unavailable"))

    setUnauthorizedHandler(unauthorizedHandler)
    httpClient.defaults.adapter = async (config) => {
      const error = new Error("Unauthorized")
      error.config = config
      error.response = { status: 401, config }
      throw error
    }

    await expect(httpClient.get("/api/users/profile")).rejects.toMatchObject({
      response: { status: 401 },
    })
    expect(unauthorizedHandler).toHaveBeenCalledOnce()
    expect(refreshRequest).toHaveBeenCalledWith("/api/auth/refresh", null, {
      withCredentials: true,
    })
  })
})
