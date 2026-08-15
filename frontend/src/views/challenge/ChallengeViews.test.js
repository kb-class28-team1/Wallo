import { ref } from "vue"
import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import ChallengeRankingView from "./ChallengeRankingView.vue"
import MyFeedView from "./MyFeedView.vue"
import { grantWeeklyRankingRewardsForTest } from "@/api/challengeApi"
import { useChallengeStore } from "@/stores/challengeStore"
import { useMyFeedStore } from "@/stores/myFeedStore"
import { useUserStore } from "@/stores/userStore"

const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
}))

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: mocks.routerPush }),
}))

vi.mock("@/api/challengeApi", () => ({
  grantWeeklyRankingRewardsForTest: vi.fn(),
}))

vi.mock("@/stores/challengeStore", () => ({
  useChallengeStore: vi.fn(),
}))

vi.mock("@/stores/myFeedStore", () => ({
  useMyFeedStore: vi.fn(),
}))

vi.mock("@/stores/userStore", () => ({
  useUserStore: vi.fn(),
}))

const rankingRows = [
  {
    rank: 1,
    nickname: "첫 번째 절약왕",
    profileImageUrl: "",
    savingAmount: 1_000_000,
    streakDays: 7,
    likeCount: 30,
  },
  {
    rank: 2,
    nickname: "두 번째 절약왕",
    profileImageUrl: "",
    savingAmount: 800_000,
    streakDays: 6,
    likeCount: 20,
  },
  {
    rank: 3,
    nickname: "세 번째 절약왕",
    profileImageUrl: "",
    savingAmount: 600_000,
    streakDays: 5,
    likeCount: 10,
  },
  {
    rank: 4,
    nickname: "네 번째 절약왕",
    profileImageUrl: "",
    savingAmount: 500_000,
    streakDays: 4,
    likeCount: 8,
  },
]

const createRankingStore = () => ({
  startDate: ref("2026-08-10"),
  endDate: ref("2026-08-16"),
  rankings: ref(rankingRows),
  myRanking: ref(rankingRows[3]),
  initialLoading: ref(false),
  refreshing: ref(false),
  errorMessage: ref(""),
  fetchWeeklyRanking: vi.fn().mockResolvedValue(rankingRows),
})

const createFeedStore = () => ({
  feeds: ref([
    {
      feedId: 101,
      challengeId: 7,
      caption: "이번 주 식비를 줄였어요",
      category: "FOOD",
      createdAt: "2026-08-14T09:00:00",
      likeCount: 12,
      commentCount: 3,
      savingAmount: 25_000,
    },
  ]),
  summary: ref({
    postCount: 4,
    receivedLikeCount: 12,
    commentCount: 3,
    totalSavingAmount: 100_000,
  }),
  sort: ref("LIKE_DESC"),
  category: ref("ALL"),
  page: ref(0),
  size: ref(10),
  totalPages: ref(2),
  initialLoading: ref(false),
  refreshing: ref(false),
  isFeedLoading: ref(false),
  errorMessage: ref(""),
  initializeMyFeedPage: vi.fn().mockResolvedValue(undefined),
  refreshMyFeedPage: vi.fn().mockResolvedValue(undefined),
  changeSort: vi.fn().mockResolvedValue(undefined),
  changeCategory: vi.fn().mockResolvedValue(undefined),
  changePage: vi.fn().mockResolvedValue(undefined),
})

const globalStubs = {
  AuthenticatedImage: {
    props: ["src", "alt"],
    template: '<img :src="src" :alt="alt" />',
  },
  RouterLink: {
    props: ["to"],
    template: '<a :href="to"><slot /></a>',
  },
}

describe("challenge views", () => {
  let rankingStore
  let feedStore
  let userStore

  beforeEach(() => {
    rankingStore = createRankingStore()
    feedStore = createFeedStore()
    userStore = {
      restoreSession: vi.fn().mockResolvedValue(undefined),
    }
    useChallengeStore.mockReturnValue(rankingStore)
    useMyFeedStore.mockReturnValue(feedStore)
    useUserStore.mockReturnValue(userStore)
    grantWeeklyRankingRewardsForTest.mockResolvedValue({ rewardedCount: 1 })
    mocks.routerPush.mockReset()
    vi.stubGlobal("alert", vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it("loads the weekly ranking and keeps the podium order", async () => {
    const wrapper = mount(ChallengeRankingView, { global: { stubs: globalStubs } })

    await flushPromises()

    expect(rankingStore.fetchWeeklyRanking).toHaveBeenCalledOnce()
    expect(wrapper.findAll(".podium-nickname").map((item) => item.text())).toEqual([
      "두 번째 절약왕",
      "첫 번째 절약왕",
      "세 번째 절약왕",
    ])
    expect(wrapper.findAll(".ranking-item")).toHaveLength(1)
    expect(wrapper.text()).toContain("네 번째 절약왕")
    expect(wrapper.text()).toContain("2026.08.10 ~ 2026.08.16")

    wrapper.unmount()
  })

  it("grants the weekly reward and refreshes the ranking and session", async () => {
    const wrapper = mount(ChallengeRankingView, { global: { stubs: globalStubs } })

    await flushPromises()
    await wrapper.get(".test-reward-button").trigger("click")
    await flushPromises()

    expect(grantWeeklyRankingRewardsForTest).toHaveBeenCalledOnce()
    expect(alert).toHaveBeenCalledWith("주간 랭킹 보상이 지급되었습니다.")
    expect(userStore.restoreSession).toHaveBeenCalledWith(true)
    expect(rankingStore.fetchWeeklyRanking).toHaveBeenLastCalledWith({ force: true })

    wrapper.unmount()
  })

  it("renders my feed summaries and routes to the focused challenge post", async () => {
    const wrapper = mount(MyFeedView, { global: { stubs: globalStubs } })

    await flushPromises()

    expect(feedStore.initializeMyFeedPage).toHaveBeenCalledOnce()
    expect(wrapper.findAll(".summary-card")).toHaveLength(4)
    expect(wrapper.text()).toContain("이번 주 식비를 줄였어요")
    expect(wrapper.text()).toContain("25,000원")

    await wrapper.findAll("select")[1].setValue("FOOD")
    expect(feedStore.changeCategory).toHaveBeenCalledWith("FOOD")

    await wrapper.get(".feed-card").trigger("click")
    expect(mocks.routerPush).toHaveBeenCalledWith({
      name: "challenge-feed",
      params: { challengeId: 7 },
      query: { scope: "ME", focusFeedId: 101 },
    })

    wrapper.unmount()
  })
})
