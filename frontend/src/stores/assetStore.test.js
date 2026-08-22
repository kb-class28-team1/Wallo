import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { getAssets, syncAssets as requestAssetSync } from "@/api/assetApi"
import { useFinancialInvalidationStore } from "@/stores/financialInvalidationStore"
import { useAssetStore } from "./assetStore"

vi.mock("@/api/assetApi", () => ({
  getAssets: vi.fn(),
  syncAssets: vi.fn(),
}))

describe("assetStore manual synchronization", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it("stores the synchronization result after a successful request", async () => {
    const result = {
      syncedAt: "2026-08-12T10:00:00",
      inserted: 3,
      updated: 42,
      failedConnections: 0,
    }
    requestAssetSync.mockResolvedValue({ success: true, data: result })
    const store = useAssetStore()

    await expect(store.syncAssets({ notifyError: false })).resolves.toEqual(result)

    expect(requestAssetSync).toHaveBeenCalledOnce()
    expect(store.lastSyncResult).toEqual(result)
    expect(store.syncError).toBeNull()
    expect(store.isSyncing).toBe(false)
    expect(useFinancialInvalidationStore().revision).toBe(1)
  })

  it("records and rethrows synchronization errors without alerting when disabled", async () => {
    const error = new Error("sync unavailable")
    requestAssetSync.mockRejectedValue(error)
    const store = useAssetStore()

    await expect(store.syncAssets({ notifyError: false })).rejects.toBe(error)

    expect(store.syncError).toBe("sync unavailable")
    expect(store.isSyncing).toBe(false)
  })

  it("does not start a second synchronization while one is in progress", async () => {
    let resolveRequest
    requestAssetSync.mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve
      }),
    )
    const store = useAssetStore()

    const firstRequest = store.syncAssets({ notifyError: false })
    const secondRequest = store.syncAssets({ notifyError: false })

    await expect(secondRequest).resolves.toBeNull()
    expect(requestAssetSync).toHaveBeenCalledOnce()

    const result = {
      syncedAt: "2026-08-12T10:00:00",
      inserted: 0,
      updated: 1,
      failedConnections: 0,
    }
    resolveRequest({ success: true, data: result })

    await expect(firstRequest).resolves.toEqual(result)
    expect(store.isSyncing).toBe(false)
  })

  it("keeps asset fetching independent from manual synchronization", async () => {
    const assets = { totalAssets: 1_000_000 }
    getAssets.mockResolvedValue({ success: true, data: assets })
    const store = useAssetStore()

    await expect(store.fetchAssets({ notifyError: false })).resolves.toEqual(assets)

    expect(getAssets).toHaveBeenCalledOnce()
    expect(store.assets).toEqual(assets)
  })

  it("reuses an in-flight request and separates initial and refresh states", async () => {
    let resolveRequest
    getAssets.mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve
      }),
    )
    const store = useAssetStore()

    const firstRequest = store.fetchAssets({ notifyError: false })

    expect(store.initialLoading).toBe(true)
    expect(store.refreshing).toBe(false)

    const secondRequest = store.fetchAssets({ notifyError: false })
    expect(getAssets).toHaveBeenCalledOnce()

    resolveRequest({ success: true, data: { totalAssets: 1_000_000 } })
    await Promise.all([firstRequest, secondRequest])

    expect(store.initialLoading).toBe(false)
    expect(store.refreshing).toBe(false)

    getAssets.mockResolvedValue({ success: true, data: { totalAssets: 2_000_000 } })
    const refreshRequest = store.fetchAssets({ notifyError: false, force: true })

    expect(store.initialLoading).toBe(false)
    expect(store.refreshing).toBe(true)

    await refreshRequest

    expect(store.refreshing).toBe(false)
    expect(store.assets.totalAssets).toBe(2_000_000)
    expect(getAssets).toHaveBeenCalledTimes(2)
  })

  it("serves a fresh asset cache without requesting the API again", async () => {
    getAssets.mockResolvedValue({ success: true, data: { totalAssets: 1_000_000 } })
    const store = useAssetStore()

    await store.fetchAssets({ notifyError: false })
    await store.fetchAssets({ notifyError: false })

    expect(getAssets).toHaveBeenCalledOnce()
  })

  it("refetches assets after a shared financial invalidation", async () => {
    getAssets.mockResolvedValue({ success: true, data: { totalAssets: 1_000_000 } })
    const store = useAssetStore()
    const invalidationStore = useFinancialInvalidationStore()

    await store.fetchAssets({ notifyError: false })
    invalidationStore.markChanged()
    getAssets.mockResolvedValue({ success: true, data: { totalAssets: 2_000_000 } })
    await store.fetchAssets({ notifyError: false })

    expect(getAssets).toHaveBeenCalledTimes(2)
    expect(store.assets.totalAssets).toBe(2_000_000)
  })

  it("does not let an old in-flight response overwrite a newer revision", async () => {
    let resolveOldRequest
    getAssets.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOldRequest = resolve
        }),
    )
    const store = useAssetStore()
    const invalidationStore = useFinancialInvalidationStore()

    const oldRequest = store.fetchAssets({ notifyError: false })
    invalidationStore.markChanged()
    getAssets.mockResolvedValueOnce({ success: true, data: { totalAssets: 2_000_000 } })
    const currentRequest = store.fetchAssets({ notifyError: false })

    resolveOldRequest({ success: true, data: { totalAssets: 1_000_000 } })
    await Promise.all([oldRequest, currentRequest])

    expect(getAssets).toHaveBeenCalledTimes(2)
    expect(store.assets.totalAssets).toBe(2_000_000)
    expect(store.initialLoading).toBe(false)
    expect(store.refreshing).toBe(false)
  })
})
