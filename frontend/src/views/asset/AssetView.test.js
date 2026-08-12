import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import AssetView from "./AssetView.vue";
import { useAssetStore } from "@/stores/assetStore";

vi.mock("@/stores/assetStore", () => ({
  useAssetStore: vi.fn(),
}));

const createStore = () => ({
  assets: ref({ totalAssets: 1_000_000 }),
  error: ref(null),
  isAssetLoading: ref(false),
  isSyncing: ref(false),
  syncError: ref(null),
  fetchAssets: vi.fn().mockResolvedValue({ totalAssets: 1_000_000 }),
  syncAssets: vi.fn(),
});

const globalStubs = {
  RouterLink: { template: "<a><slot /></a>" },
  AssetOverviewCard: { template: '<div data-testid="asset-overview-card" />' },
  ConsumptionReportCard: { template: '<div data-testid="consumption-report-card" />' },
  TaxDeductionTrackerCard: { template: '<div data-testid="tax-deduction-card" />' },
};

describe("AssetView manual synchronization", () => {
  let wrapper;
  let store;

  beforeEach(async () => {
    store = createStore();
    store.syncAssets.mockResolvedValue({
      syncedAt: "2026-08-12T10:00:00",
      inserted: 3,
      updated: 42,
      failedConnections: 0,
    });
    useAssetStore.mockReturnValue(store);
    wrapper = mount(AssetView, { global: { stubs: globalStubs } });
    await flushPromises();
  });

  afterEach(() => {
    wrapper?.unmount();
    vi.clearAllMocks();
  });

  it("syncs assets and refreshes the displayed data", async () => {
    await wrapper.get(".asset-sync-button").trigger("click");
    await flushPromises();

    expect(store.syncAssets).toHaveBeenCalledOnce();
    expect(store.fetchAssets).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("동기화가 완료되었습니다");
    expect(wrapper.text()).toContain("신규 3건, 수정 42건");
  });

  it("disables the button while synchronization is in progress", async () => {
    let finishSync;
    store.syncAssets.mockImplementation(() => {
      store.isSyncing.value = true;
      return new Promise((resolve) => {
        finishSync = (result) => {
          store.isSyncing.value = false;
          resolve(result);
        };
      });
    });

    const syncButton = wrapper.get(".asset-sync-button");
    const request = syncButton.trigger("click");
    await flushPromises();

    expect(syncButton.element.disabled).toBe(true);
    expect(syncButton.text()).toContain("동기화 중");

    finishSync({
      syncedAt: "2026-08-12T10:00:00",
      inserted: 0,
      updated: 1,
      failedConnections: 0,
    });
    await request;
    await flushPromises();

    expect(syncButton.element.disabled).toBe(false);
  });

  it("shows partial failure information without discarding successful data", async () => {
    store.syncAssets.mockResolvedValue({
      syncedAt: "2026-08-12T10:00:00",
      inserted: 1,
      updated: 2,
      failedConnections: 1,
    });

    await wrapper.get(".asset-sync-button").trigger("click");
    await flushPromises();

    expect(store.fetchAssets).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("실패한 연결기관 1건");
  });

  it("shows the synchronization error when the request fails", async () => {
    store.syncAssets.mockRejectedValue(new Error("CODEF unavailable"));
    store.syncError.value = "CODEF unavailable";

    await wrapper.get(".asset-sync-button").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("CODEF unavailable");
    expect(store.fetchAssets).toHaveBeenCalledOnce();
  });
});
