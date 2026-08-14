import { ref } from "vue"
import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import PointHistoryView from "./PointHistoryView.vue"
import PointShopView from "./PointShopView.vue"
import { getPointHistory } from "@/api/pointHistoryApi"
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
    expect(wrapper.find(".random-box-card").text()).toContain("기본 절약 상자")
    expect(wrapper.find(".inventory-item").text()).toContain("아메리카노 기프티콘")

    await wrapper.get(".open-box-button").trigger("click")
    await flushPromises()

    expect(openRandomBox).toHaveBeenCalledWith(2)
    expect(userStore.updatePointBalance).toHaveBeenLastCalledWith(950)
    expect(getPointShop).toHaveBeenCalledTimes(2)
    expect(wrapper.find(".reward-modal-backdrop").text()).toContain("250P 당첨!")

    wrapper.unmount()
  })
})
