import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { flushPromises, mount } from "@vue/test-utils"
import ConnectionManagementView from "./ConnectionManagementView.vue"
import { disconnectConnection, getConnections } from "@/api/connectionApi"
import { useAssetStore } from "@/stores/assetStore"

vi.mock("@/api/connectionApi", () => ({
  disconnectConnection: vi.fn(),
  getConnections: vi.fn(),
}))

vi.mock("@/stores/assetStore", () => ({
  useAssetStore: vi.fn(),
}))

const connectedAssets = [
  {
    connectionId: 10,
    institutionId: 1,
    institutionName: "국민은행",
    financialGroupCode: "KB",
    financialGroupName: "KB금융그룹",
    logoUrl: "",
    lastSyncAt: "2026-08-06T01:00:00",
    assetKind: "ACCOUNT",
    assetId: 101,
    assetName: "KB국민ONE통장",
    displayNumber: "123456-**-***012",
    assetType: "BANK",
    amount: 5000000,
    currency: "KRW",
  },
  {
    connectionId: 10,
    institutionId: 1,
    institutionName: "국민은행",
    financialGroupCode: "KB",
    financialGroupName: "KB금융그룹",
    logoUrl: "",
    lastSyncAt: "2026-08-06T01:00:00",
    assetKind: "CARD",
    assetId: 102,
    assetName: "국민카드",
    displayNumber: "9876-****-****-3210",
    assetType: "CREDIT",
    amount: 120000,
    currency: "KRW",
  },
  {
    connectionId: 10,
    institutionId: 1,
    institutionName: "국민은행",
    financialGroupCode: "KB",
    financialGroupName: "KB금융그룹",
    logoUrl: "",
    lastSyncAt: "2026-08-06T01:00:00",
    assetKind: "ACCOUNT",
    assetId: 103,
    assetName: "일반 상환 학자금대출",
    displayNumber: "STUDENT-LOAN-****-001",
    assetType: "LOAN",
    amount: -4800000,
    currency: "KRW",
  },
]

describe("ConnectionManagementView", () => {
  let wrapper
  let assetStore

  beforeEach(() => {
    getConnections.mockResolvedValue({ connections: connectedAssets })
    disconnectConnection.mockResolvedValue({})
    assetStore = {
      fetchAssets: vi.fn().mockResolvedValue({}),
      invalidateAssetsCache: vi.fn(),
    }
    useAssetStore.mockReturnValue(assetStore)
  })

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ""
    vi.clearAllMocks()
  })

  it("supports keyboard navigation for asset category tabs", async () => {
    wrapper = mount(ConnectionManagementView, {
      attachTo: document.body,
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    })
    await flushPromises()

    expect(wrapper.find(".settings-panel").classes()).toContain("app-card")
    expect(wrapper.find(".app-tabs").exists()).toBe(true)
    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs[0].classes()).toContain("app-tabs__tab")
    expect(tabs).toHaveLength(3)
    expect(tabs[0].attributes("tabindex")).toBe("0")
    expect(tabs[1].attributes("tabindex")).toBe("-1")

    await tabs[0].trigger("keydown", { key: "ArrowRight" })
    await flushPromises()

    expect(tabs[1].attributes("aria-selected")).toBe("true")
    expect(tabs[1].attributes("tabindex")).toBe("0")
    expect(document.activeElement).toBe(tabs[1].element)
  })

  it("shows an explicit confirmation state for non-canonical card types", async () => {
    getConnections.mockResolvedValueOnce({
      connections: [
        connectedAssets[1],
        { ...connectedAssets[1], assetId: 104, assetName: "체크카드", assetType: "CHECK" },
        { ...connectedAssets[1], assetId: 105, assetName: "레거시 직불카드", assetType: "DEBIT" },
        { ...connectedAssets[1], assetId: 106, assetName: "알 수 없는 카드", assetType: "UNKNOWN" },
      ],
    })
    wrapper = mount(ConnectionManagementView, {
      attachTo: document.body,
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    })
    await flushPromises()

    await wrapper.findAll('[role="tab"]')[1].trigger("click")

    const labels = wrapper
      .findAll(".connection-asset .connection-information strong")
      .map((element) => element.text())
    expect(labels).toEqual([
      "신용카드",
      "체크카드",
      "카드 유형 확인 필요",
      "카드 유형 확인 필요",
    ])
  })

  it("prioritizes the local KB logo over an API logo URL", async () => {
    getConnections.mockResolvedValueOnce({
      connections: [
        {
          ...connectedAssets[0],
          logoUrl: "https://www.kbstar.com/favicon.ico",
        },
      ],
    })
    wrapper = mount(ConnectionManagementView, {
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    })
    await flushPromises()

    expect(wrapper.find(".asset-logo-image").attributes("src")).toBe(
      "/images/institutions/kb.webp",
    )
  })

  it("groups an institution into one disconnect modal and refreshes assets", async () => {
    wrapper = mount(ConnectionManagementView, {
      attachTo: document.body,
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    })
    await flushPromises()

    expect(wrapper.findAll(".connection-institution")).toHaveLength(1)
    expect(wrapper.findAll(".connection-institution .connection-disconnect")).toHaveLength(1)
    expect(wrapper.text()).toContain("마지막 동기화")
    expect(wrapper.text()).toContain("-4,800,000원")

    await wrapper.find(".connection-disconnect").trigger("click")
    await flushPromises()

    const dialog = document.body.querySelector('[role="dialog"]')
    expect(dialog).not.toBeNull()
    expect(dialog.textContent).toContain("국민은행 연결을 해제하시겠습니까?")
    expect(dialog.textContent).toContain("KB국민ONE통장 · 123456-**-***012")
    expect(dialog.textContent).toContain("국민카드 · 9876-****-****-3210")
    expect(dialog.textContent).toContain("일반 상환 학자금대출 · STUDENT-LOAN-****-001")
    expect(dialog.textContent).not.toContain("입출금")
    expect(dialog.textContent).not.toContain("5,000,000원")

    dialog.querySelector(".btn-danger").dispatchEvent(new MouseEvent("click", { bubbles: true }))
    await flushPromises()

    expect(disconnectConnection).toHaveBeenCalledWith(10)
    expect(assetStore.invalidateAssetsCache).toHaveBeenCalledOnce()
    expect(assetStore.fetchAssets).toHaveBeenCalledWith({ notifyError: false })
    expect(document.body.querySelector('[role="dialog"]')).toBeNull()
    expect(document.activeElement).toBe(wrapper.find('[role="tab"]').element)
  })

  it("renders a shared empty state for each asset category", async () => {
    getConnections.mockResolvedValueOnce({ connections: [] })
    wrapper = mount(ConnectionManagementView, {
      attachTo: document.body,
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    })
    await flushPromises()

    expect(wrapper.find(".connection-empty-state").attributes("role")).toBe("tabpanel")
    expect(wrapper.find(".connection-empty-state .app-state").exists()).toBe(true)
    expect(wrapper.find(".connection-empty-state .app-state").attributes("data-state")).toBe(
      "empty",
    )
    expect(wrapper.find(".connection-empty-state").text()).toContain("연결된 계좌가 없습니다.")

    await wrapper.findAll('[role="tab"]')[1].trigger("click")

    expect(wrapper.find(".connection-empty-state").text()).toContain("연결된 카드가 없습니다.")
  })

  it("shows a shared error alert when the initial connection load fails", async () => {
    getConnections.mockRejectedValueOnce(new Error("연결 조회 실패"))
    wrapper = mount(ConnectionManagementView, {
      attachTo: document.body,
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    })
    await flushPromises()

    expect(wrapper.find(".connection-alert").classes()).toContain("app-alert")
    expect(wrapper.find(".connection-alert").classes()).toContain("app-alert--danger")
    expect(wrapper.find('[role="alert"]').text()).toContain("연결 조회 실패")
  })
})
