import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getAssets, syncAssets as requestAssetSync } from "@/api/assetApi";
import { useAssetStore } from "./assetStore";

vi.mock("@/api/assetApi", () => ({
  getAssets: vi.fn(),
  syncAssets: vi.fn(),
}));

describe("assetStore manual synchronization", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("stores the synchronization result after a successful request", async () => {
    const result = {
      syncedAt: "2026-08-12T10:00:00",
      inserted: 3,
      updated: 42,
      failedConnections: 0,
    };
    requestAssetSync.mockResolvedValue({ success: true, data: result });
    const store = useAssetStore();

    await expect(store.syncAssets({ notifyError: false })).resolves.toEqual(result);

    expect(requestAssetSync).toHaveBeenCalledOnce();
    expect(store.lastSyncResult).toEqual(result);
    expect(store.syncError).toBeNull();
    expect(store.isSyncing).toBe(false);
  });

  it("records and rethrows synchronization errors without alerting when disabled", async () => {
    const error = new Error("sync unavailable");
    requestAssetSync.mockRejectedValue(error);
    const store = useAssetStore();

    await expect(store.syncAssets({ notifyError: false })).rejects.toBe(error);

    expect(store.syncError).toBe("sync unavailable");
    expect(store.isSyncing).toBe(false);
  });

  it("does not start a second synchronization while one is in progress", async () => {
    let resolveRequest;
    requestAssetSync.mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve;
    }));
    const store = useAssetStore();

    const firstRequest = store.syncAssets({ notifyError: false });
    const secondRequest = store.syncAssets({ notifyError: false });

    await expect(secondRequest).resolves.toBeNull();
    expect(requestAssetSync).toHaveBeenCalledOnce();

    const result = {
      syncedAt: "2026-08-12T10:00:00",
      inserted: 0,
      updated: 1,
      failedConnections: 0,
    };
    resolveRequest({ success: true, data: result });

    await expect(firstRequest).resolves.toEqual(result);
    expect(store.isSyncing).toBe(false);
  });

  it("keeps asset fetching independent from manual synchronization", async () => {
    const assets = { totalAssets: 1_000_000 };
    getAssets.mockResolvedValue({ success: true, data: assets });
    const store = useAssetStore();

    await expect(store.fetchAssets({ notifyError: false })).resolves.toEqual(assets);

    expect(getAssets).toHaveBeenCalledOnce();
    expect(store.assets).toEqual(assets);
  });
});
