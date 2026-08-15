import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  clearResourceCache,
  getCachedResource,
  getResource,
  hasInFlightResource,
  invalidateResource,
  setCachedResource,
} from "./resourceCache"

describe("resourceCache", () => {
  beforeEach(() => {
    clearResourceCache()
  })

  afterEach(() => {
    clearResourceCache()
  })

  it("caches a resource and reloads only when forced", async () => {
    const loader = vi.fn().mockResolvedValue({ value: 1 })

    await expect(getResource("summary", loader)).resolves.toEqual({ value: 1 })
    await expect(getResource("summary", loader)).resolves.toEqual({ value: 1 })

    expect(loader).toHaveBeenCalledOnce()
    expect(getCachedResource("summary")).toEqual({ value: 1 })

    await expect(getResource("summary", loader, { force: true }))
      .resolves.toEqual({ value: 1 })

    expect(loader).toHaveBeenCalledTimes(2)
  })

  it("deduplicates in-flight requests and clears their state after completion", async () => {
    let resolveRequest
    const loader = vi.fn().mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve
    }))

    const firstRequest = getResource("ranking", loader)
    const secondRequest = getResource("ranking", loader)

    expect(secondRequest).toBe(firstRequest)
    expect(hasInFlightResource("ranking")).toBe(true)
    expect(loader).not.toHaveBeenCalled()

    await Promise.resolve()
    expect(loader).toHaveBeenCalledOnce()

    resolveRequest({ value: 2 })
    await expect(firstRequest).resolves.toEqual({ value: 2 })
    expect(hasInFlightResource("ranking")).toBe(false)
  })

  it("isolates scopes and supports targeted invalidation", () => {
    const scope = {}
    setCachedResource("asset", "scoped", { scope })
    setCachedResource("asset", "default")

    expect(getCachedResource("asset", { scope })).toBe("scoped")
    expect(getCachedResource("asset")).toBe("default")

    invalidateResource("asset", { scope })

    expect(getCachedResource("asset", { scope })).toBeUndefined()
    expect(getCachedResource("asset")).toBe("default")
  })
})
