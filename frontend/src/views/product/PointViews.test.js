import { ref } from "vue"
import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import PointHistoryView from "./PointHistoryView.vue"
import PointShopView from "./PointShopView.vue"
import { getPointHistory } from "@/api/pointHistoryApi"
import QRCode from "qrcode"
import {
  deleteUsedInventoryItem,
  getPointShop,
  openRandomBox,
  openRandomBoxes,
  useInventoryItem,
} from "@/api/pointShopApi"
import { useUserStore } from "@/stores/userStore"
import { clearResourceCache } from "@/utils/resourceCache"

const mocks = vi.hoisted(() => ({
  routerBack: vi.fn(),
  routerPush: vi.fn(),
}))

vi.mock("vue-router", () => ({
  useRouter: () => ({
    back: mocks.routerBack,
    push: mocks.routerPush,
    currentRoute: { value: { name: "point-shop" } },
  }),
}))

vi.mock("@/api/pointHistoryApi", () => ({
  getPointHistory: vi.fn(),
}))

vi.mock("qrcode", () => ({
  default: {
    toDataURL: vi.fn(),
  },
}))

vi.mock("@/api/pointShopApi", () => ({
  deleteUsedInventoryItem: vi.fn(),
  getPointShop: vi.fn(),
  openRandomBox: vi.fn(),
  openRandomBoxes: vi.fn(),
  useInventoryItem: vi.fn(),
}))

vi.mock("@/stores/userStore", () => ({
  useUserStore: vi.fn(),
}))

const historyPayload = {
  summary: {
    totalEarned: 1_000,
    totalUsed: 200,
    balance: 800,
    monthlyChange: 300,
  },
  items: [
    {
      id: 1,
      type: "EARN",
      title: "챌린지 보상",
      description: "절약 인증 보상",
      category: "CHALLENGE",
      createdAt: "2026-08-14T09:00:00",
      amount: 250,
      status: "적립 완료",
    },
  ],
  totalPages: 1,
}

const pointShopPayload = {
  pointBalance: 1_200,
  boxes: [
    {
      boxId: 2,
      boxName: "기본 절약 상자",
      price: 500,
    },
  ],
  inventory: [
    {
      inventoryId: 10,
      itemName: "아메리카노 기프티콘",
      acquiredAt: "2026-08-14",
      status: "AVAILABLE",
    },
  ],
}

const globalStubs = {
  RouterLink: {
    props: ["to"],
    template: '<a :href="to"><slot /></a>',
  },
}

describe("point views", () => {
  let userStore

  beforeEach(() => {
    clearResourceCache()
    userStore = {
      user: ref({ id: 7 }),
      pointBalance: ref(1_200),
      updatePointBalance: vi.fn(),
    }
    useUserStore.mockReturnValue(userStore)
    getPointHistory.mockResolvedValue({ data: { data: historyPayload } })
    getPointShop.mockResolvedValue({ data: pointShopPayload })
    openRandomBox.mockResolvedValue({
      data: { result: "POINT", rewardPoint: 250, remainingPoint: 950 },
    })
    openRandomBoxes.mockResolvedValue({ data: {} })
    deleteUsedInventoryItem.mockResolvedValue({})
    useInventoryItem.mockResolvedValue({})
    QRCode.toDataURL.mockResolvedValue("data:image/png;base64,test-qr")
    mocks.routerBack.mockReset()
    mocks.routerPush.mockReset()
    vi.stubGlobal("alert", vi.fn())
  })

  afterEach(() => {
    clearResourceCache()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it("loads point history and reloads when the earning filter changes", async () => {
    const wrapper = mount(PointHistoryView, { global: { stubs: globalStubs } })

    await flushPromises()

    expect(getPointHistory).toHaveBeenCalledWith({
      type: "ALL",
      period: "ALL",
      sort: "LATEST",
      keyword: undefined,
      page: 0,
      size: 20,
    })
    expect(wrapper.text()).toContain("1,000P")
    expect(wrapper.text()).toContain("챌린지 보상")
    expect(wrapper.text()).toContain("+250P")
    expect(wrapper.find(".page-heading").classes()).toContain("app-page-header")
    expect(wrapper.find(".summary-card").classes()).toContain("app-card")
    expect(wrapper.find(".filter-panel").classes()).toContain("app-card")
    expect(wrapper.find(".filter-tab").classes()).toContain("app-button")

    await wrapper.findAll(".filter-tab")[1].trigger("click")
    await flushPromises()

    expect(getPointHistory).toHaveBeenCalledTimes(2)
    expect(getPointHistory).toHaveBeenLastCalledWith({
      type: "EARN",
      period: "ALL",
      sort: "LATEST",
      keyword: undefined,
      page: 0,
      size: 20,
    })

    wrapper.unmount()
  })

  it("opens a point box, updates the balance, and shows the reward", async () => {
    getPointShop.mockReset()
    getPointShop
      .mockResolvedValueOnce({ data: pointShopPayload })
      .mockResolvedValue({ data: { ...pointShopPayload, pointBalance: 950 } })

    const router = {
      back: mocks.routerBack,
      push: mocks.routerPush,
      currentRoute: { value: { name: "point-shop" } },
    }
    const wrapper = mount(PointShopView, {
      global: {
        stubs: globalStubs,
        mocks: { $router: router },
      },
    })

    await flushPromises()

    expect(getPointShop).toHaveBeenCalledOnce()
    expect(wrapper.find(".point-summary-card").text()).toContain("1,200P")
    expect(wrapper.find(".page-heading").classes()).toContain("app-page-header")
    expect(wrapper.find(".point-summary-card").classes()).toContain("app-card")
    expect(wrapper.find(".random-box-card").classes()).toContain("app-card")
    expect(wrapper.find(".draw-machine-preview-lever").exists()).toBe(true)
    expect(wrapper.find(".bulk-open-box-button").classes()).toContain("app-button")
    expect(wrapper.find(".random-box-card").text()).toContain("기본 절약 상자")
    expect(wrapper.find(".inventory-item").text()).toContain("아메리카노 기프티콘")

    await wrapper.get(".inventory-item").trigger("click")
    expect(wrapper.find('[role="dialog"]').classes()).toContain("app-dialog")
    expect(wrapper.find(".app-dialog-confirm").text()).toContain("사용하기")
    await wrapper.get(".app-dialog-cancel").trigger("click")
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    await wrapper.get(".inventory-item").trigger("click")
    await wrapper.get(".app-dialog-confirm").trigger("click")
    await flushPromises()

    expect(useInventoryItem).not.toHaveBeenCalled()
    expect(QRCode.toDataURL).toHaveBeenCalledWith(
      expect.stringMatching(/^WALLO-SIMULATED-COUPON:10:/),
      expect.objectContaining({ width: 240, errorCorrectionLevel: "M" }),
    )
    expect(wrapper.text()).toContain("테스트용 임의 QR")
    expect(wrapper.find('img[alt="아메리카노 기프티콘 임의 QR 코드"]').exists()).toBe(true)

    await wrapper.get(".app-dialog-confirm").trigger("click")
    expect(wrapper.text()).not.toContain("테스트용 임의 QR")

    await wrapper.get(".draw-machine-preview-lever").trigger("click")
    await flushPromises()

    expect(openRandomBox).toHaveBeenCalledWith(2)
    expect(userStore.updatePointBalance).toHaveBeenLastCalledWith(950)
    expect(getPointShop).toHaveBeenCalledTimes(2)
    expect(wrapper.find(".reward-modal-backdrop").text()).toContain("250P 당첨!")
    expect(wrapper.find(".reward-celebration").exists()).toBe(true)
    expect(wrapper.find(".reward-modal-confirm-outside").exists()).toBe(true)
    expect(wrapper.find(".draw-machine-backdrop").exists()).toBe(false)

    wrapper.unmount()
  })
})
