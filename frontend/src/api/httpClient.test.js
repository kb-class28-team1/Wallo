import { afterEach, describe, expect, it, vi } from "vitest"
import httpClient, { setUnauthorizedHandler } from "./httpClient"

describe("httpClient authentication errors", () => {
  let originalAdapter

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter
    setUnauthorizedHandler(null)
  })

  it("routes profile API 401 responses through the global unauthorized handler", async () => {
    originalAdapter = httpClient.defaults.adapter
    const unauthorizedHandler = vi.fn()

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
  })
})
