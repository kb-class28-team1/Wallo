import { nextTick, ref } from "vue"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { flushPromises, mount } from "@vue/test-utils"
import AssetView from "./AssetView.vue"
import { useAssetStore } from "@/stores/assetStore"

vi.mock("@/stores/assetStore", () => ({
  useAssetStore: vi.fn(),
}))

const createStore = () => ({
  assets: ref({ totalAssets: 1_000_000 }),
  error: ref(null),
  initialLoading: ref(false),
  refreshing: ref(false),
  isAssetLoading: ref(false),
  isSyncing: ref(false),
  syncError: ref(null),
  fetchAssets: vi.fn().mockResolvedValue({ totalAssets: 1_000_000 }),
  syncAssets: vi.fn(),
})

const globalStubs = {
  RouterLink: { template: "<a><slot /></a>" },
  AssetOverviewCard: { template: '<div data-testid="asset-overview-card" />' },
  ConsumptionReportCard: { template: '<div data-testid="consumption-report-card" />' },
  TaxDeductionTrackerCard: { template: '<div data-testid="tax-deduction-card" />' },
}

describe("AssetView manual synchronization", () => {
  let wrapper
  let store

  beforeEach(async () => {
    store = createStore()
    store.syncAssets.mockResolvedValue({
      syncedAt: "2026-08-12T10:00:00",
      inserted: 3,
      updated: 42,
      failedConnections: 0,
    })
    useAssetStore.mockReturnValue(store)
    wrapper = mount(AssetView, { global: { stubs: globalStubs } })
    await flushPromises()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it("syncs assets and refreshes the displayed data", async () => {
    expect(wrapper.find(".app-page-header__title").text()).toBe("자산관리")
    expect(wrapper.find(".asset-sync-button").classes()).toContain("app-action-link")
    expect(wrapper.find(".asset-sync-button").text()).toContain("새로고침")
    expect(wrapper.find(".asset-sync-button .bi-arrow-clockwise").exists()).toBe(true)
    expect(wrapper.find(".asset-report-grid").exists()).toBe(true)
    expect(wrapper.find('[data-testid="consumption-report-card"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tax-deduction-card"]').exists()).toBe(true)

    await wrapper.get(".asset-sync-button").trigger("click")
    await flushPromises()

    expect(store.syncAssets).toHaveBeenCalledOnce()
    expect(store.fetchAssets).toHaveBeenCalledTimes(2)
    expect(wrapper.find(".asset-sync-status").classes()).toContain("app-alert")
    expect(wrapper.find(".asset-sync-status").classes()).toContain("app-alert--success")
    expect(wrapper.find(".asset-sync-status").attributes("role")).toBe("status")
    expect(wrapper.text()).toContain("동기화가 완료되었습니다")
    expect(wrapper.text()).toContain("신규 3건, 수정 42건")
  })

  it("disables the button while synchronization is in progress", async () => {
    let finishSync
    store.syncAssets.mockImplementation(() => {
      store.isSyncing.value = true
      return new Promise((resolve) => {
        finishSync = (result) => {
          store.isSyncing.value = false
          resolve(result)
        }
      })
    })

    const syncButton = wrapper.get(".asset-sync-button")
    const request = syncButton.trigger("click")
    await flushPromises()

    expect(syncButton.element.disabled).toBe(true)
    expect(syncButton.text()).toContain("새로고침")

    finishSync({
      syncedAt: "2026-08-12T10:00:00",
      inserted: 0,
      updated: 1,
      failedConnections: 0,
    })
    await request
    await flushPromises()

    expect(syncButton.element.disabled).toBe(false)
  })

  it("shows partial failure information without discarding successful data", async () => {
    store.syncAssets.mockResolvedValue({
      syncedAt: "2026-08-12T10:00:00",
      inserted: 1,
      updated: 2,
      failedConnections: 1,
    })

    await wrapper.get(".asset-sync-button").trigger("click")
    await flushPromises()

    expect(store.fetchAssets).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain("실패한 연결기관 1건")
  })

  it("shows fallback classification warnings after synchronization", async () => {
    store.syncAssets.mockResolvedValue({
      syncedAt: "2026-08-12T10:00:00",
      inserted: 1,
      updated: 2,
      failedConnections: 0,
      fallbackCount: 3,
    })

    await wrapper.get(".asset-sync-button").trigger("click")
    await flushPromises()

    expect(wrapper.find(".asset-sync-status").classes()).toContain("app-alert--warning")
    expect(wrapper.text()).toContain("AI 분류 실패로 기타 처리된 거래 3건")
  })

  it("shows the synchronization error when the request fails", async () => {
    store.syncAssets.mockRejectedValue(new Error("CODEF unavailable"))
    store.syncError.value = "CODEF unavailable"

    await wrapper.get(".asset-sync-button").trigger("click")
    await flushPromises()

    expect(wrapper.text()).toContain("CODEF unavailable")
    expect(store.fetchAssets).toHaveBeenCalledOnce()
  })

  it("keeps the current asset card visible during a background refresh", async () => {
    store.refreshing.value = true
    await nextTick()

    expect(wrapper.find('[data-testid="asset-overview-card"]').exists()).toBe(true)
    expect(wrapper.text()).toContain("자산 정보를 최신 상태로 갱신하고 있습니다.")
    expect(wrapper.get(".asset-sync-button").element.disabled).toBe(true)
  })

  it("uses the shared loading state while the initial asset request is pending", async () => {
    store.assets.value = null
    store.initialLoading.value = true
    await nextTick()

    expect(wrapper.find(".asset-state").classes()).toContain("app-state")
    expect(wrapper.find(".asset-state").classes()).toContain("app-state--loading")
    expect(wrapper.find(".asset-state").attributes("role")).toBe("status")
  })

  it("uses the shared error alert when no existing asset data is available", async () => {
    store.assets.value = null
    store.initialLoading.value = false
    store.error.value = "자산 조회 실패"
    await nextTick()

    expect(wrapper.find(".asset-error").classes()).toContain("app-alert")
    expect(wrapper.find(".asset-error").classes()).toContain("app-alert--danger")
    expect(wrapper.find('[role="alert"]').text()).toContain("자산 조회 실패")

    await wrapper.find(".asset-error .app-button").trigger("click")

    expect(store.fetchAssets).toHaveBeenCalledWith({ force: true })
  })
})
