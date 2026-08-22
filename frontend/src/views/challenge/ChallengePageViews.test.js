import { nextTick } from "vue"
import { flushPromises, mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import ChallengeFeedView from "./ChallengeFeedView.vue"
import MyChallengeView from "./MyChallengeView.vue"
import { refreshAccessToken } from "@/api/authApi"
import { getAccessToken } from "@/api/authToken"
import { getMyChallengeDashboard, leaveChallenge } from "@/api/challengeApi"
import {
  addFeedLike,
  analyzeFeed,
  createFeed,
  deleteFeed,
  getFeeds,
  getRoomMessages,
} from "@/api/feedApi"
import { getTodayMissions, verifyMissionWithFeed } from "@/api/missionApi"
import { useUserStore } from "@/stores/userStore"
import { useMissionStore } from "@/stores/missionStore"
import { clearResourceCache } from "@/utils/resourceCache"

const mocks = vi.hoisted(() => ({
  route: {
    params: { challengeId: "7" },
    query: {},
  },
  routerPush: vi.fn(),
  routerReplace: vi.fn(),
}))

vi.mock("vue-router", () => ({
  RouterLink: {
    props: ["to"],
    template: '<a :href="to"><slot /></a>',
  },
  useRoute: () => mocks.route,
  useRouter: () => ({
    push: mocks.routerPush,
    replace: mocks.routerReplace,
  }),
}))

vi.mock("@/api/authApi", () => ({
  refreshAccessToken: vi.fn(),
}))

vi.mock("@/api/authToken", () => ({
  getAccessToken: vi.fn(),
}))

vi.mock("@/api/challengeApi", () => ({
  getMyChallengeDashboard: vi.fn(),
  leaveChallenge: vi.fn(),
}))

vi.mock("@/api/feedApi", () => ({
  addFeedLike: vi.fn(),
  analyzeFeed: vi.fn(),
  createFeed: vi.fn(),
  deleteFeed: vi.fn(),
  getFeeds: vi.fn(),
  getRoomMessages: vi.fn(),
}))

vi.mock("@/api/missionApi", () => ({
  completeSelfCheckMission: vi.fn(),
  generateNextDayMissions: vi.fn(),
  getTodayMissions: vi.fn(),
  verifyMissionWithFeed: vi.fn(),
  verifyTransactionMission: vi.fn(),
}))

vi.mock("@/stores/userStore", () => ({
  useUserStore: vi.fn(),
}))

vi.mock("vue-chartjs", () => ({
  Line: {
    template: '<div data-testid="challenge-chart" />',
  },
}))

const feedPayload = {
  challengeName: "주간 절약 챌린지",
  inviteCode: "SAVE-7",
  mySavingTotal: 45_000,
  feeds: [
    {
      id: 101,
      userId: 2,
      nickname: "절약왕",
      profileImageUrl: "",
      category: "FOOD",
      caption: "이번 주 식비를 줄였어요",
      savingAmount: 30_000,
      likeCount: 4,
      commentCount: 1,
      createdAt: "2026-08-14T09:00:00",
      mediaUrl: "",
      mediaType: "IMAGE",
    },
  ],
}

const messagePayload = {
  challengeName: "주간 절약 챌린지",
  messages: [],
}

const dashboardPayload = {
  nickname: "절약하는 사용자",
  profileImageUrl: "",
  joinedAt: "2026-01-01",
  currentChallengeName: "주간 절약 챌린지",
  streakDays: 5,
  verificationCount: 12,
  totalSavingAmount: 500_000,
  currentMonthSavingAmount: 100_000,
  previousMonthSavingAmount: 80_000,
  savingChangeRate: 25,
  averageSavingAmount: 41_667,
  postCount: 6,
  receivedLikeCount: 18,
  commentCount: 4,
  monthlySavings: [
    { month: "2026-07-01", savingAmount: 80_000 },
    { month: "2026-08-01", savingAmount: 100_000 },
  ],
  topLikedFeeds: [
    {
      feedId: 101,
      caption: "이번 주 식비를 줄였어요",
      createdAt: "2026-08-14T09:00:00",
      likeCount: 10,
      mediaUrl: "",
    },
  ],
}

class TestWebSocket {
  static OPEN = 1
  static CLOSED = 3

  constructor() {
    this.readyState = TestWebSocket.OPEN
  }

  close() {
    this.readyState = TestWebSocket.CLOSED
  }

  send() {}
}

const globalStubs = {
  AppDialog: { template: "<div />" },
  AuthenticatedImage: {
    props: ["src", "alt"],
    template: '<img :src="src" :alt="alt" />',
  },
  Line: {
    template: '<div data-testid="challenge-chart" />',
  },
}

const mountFeed = () =>
  mount(ChallengeFeedView, {
    global: {
      stubs: globalStubs,
    },
  })

const mountMyChallenge = () =>
  mount(MyChallengeView, {
    global: {
      stubs: {
        ...globalStubs,
        RouterLink: {
          props: ["to"],
          template: '<a :href="to"><slot /></a>',
        },
      },
    },
  })

describe("challenge page views", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    clearResourceCache()
    mocks.route.params = { challengeId: "7" }
    mocks.route.query = {}
    mocks.routerPush.mockReset()
    mocks.routerReplace.mockReset()

    useUserStore.mockReturnValue({
      user: { id: 1 },
      fetchUserProfile: vi.fn().mockResolvedValue(undefined),
    })
    getAccessToken.mockReturnValue("test-token")
    refreshAccessToken.mockResolvedValue(undefined)
    leaveChallenge.mockResolvedValue(undefined)
    getTodayMissions.mockResolvedValue({ missions: [] })
    verifyMissionWithFeed.mockResolvedValue(undefined)
    addFeedLike.mockResolvedValue({ likeCount: 5 })
    analyzeFeed.mockResolvedValue({})
    createFeed.mockResolvedValue({})
    deleteFeed.mockResolvedValue(undefined)
    getFeeds.mockResolvedValue(feedPayload)
    getRoomMessages.mockResolvedValue(messagePayload)
    getMyChallengeDashboard.mockResolvedValue(dashboardPayload)
    vi.stubGlobal("WebSocket", TestWebSocket)
  })

  afterEach(() => {
    useMissionStore().stopLifecycle()
    useMissionStore().reset()
    clearResourceCache()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it("reuses cached feed data on re-entry and keeps cards visible during tab refresh", async () => {
    const firstWrapper = mountFeed()

    await flushPromises()

    expect(getFeeds).toHaveBeenCalledWith(7, false)
    expect(getRoomMessages).toHaveBeenCalledWith(7)
    expect(firstWrapper.find(".feed-card").exists()).toBe(true)
    expect(firstWrapper.find(".feed-page-header").classes()).toContain("app-page-header")
    expect(firstWrapper.find(".feed-page-header .app-page-header__title").text()).toBe(
      "주간 절약 챌린지",
    )
    expect(firstWrapper.find(".saving-total").exists()).toBe(true)
    expect(firstWrapper.find(".feed-page-header .feed-invite-panel").exists()).toBe(false)
    expect(firstWrapper.find(".feed-header-bottom .feed-invite-panel").exists()).toBe(true)
    expect(firstWrapper.findAll(".feed-tabs .app-button")).toHaveLength(2)
    expect(firstWrapper.find(".feed-leave-button").text()).toContain("나가기")
    expect(firstWrapper.find(".mention-feed-button .bi-send").exists()).toBe(true)
    expect(firstWrapper.find(".mention-feed-button img").exists()).toBe(false)
    expect(firstWrapper.find('.chat-form button[aria-label="메시지 전송"] .bi-send').exists()).toBe(true)

    firstWrapper.unmount()

    const secondWrapper = mountFeed()
    await flushPromises()

    expect(getFeeds).toHaveBeenCalledOnce()
    expect(getRoomMessages).toHaveBeenCalledOnce()
    expect(secondWrapper.find(".page-state").exists()).toBe(false)
    expect(secondWrapper.find(".feed-card").exists()).toBe(true)

    let resolveMineFeeds
    getFeeds.mockImplementation((challengeId, mine) =>
      mine
        ? new Promise((resolve) => {
            resolveMineFeeds = resolve
          })
        : Promise.resolve(feedPayload),
    )

    await secondWrapper.findAll(".feed-tabs button")[1].trigger("click")
    await nextTick()

    expect(secondWrapper.find('[role="status"]').text()).toContain("최신 피드와 채팅")
    expect(secondWrapper.find(".feed-card").exists()).toBe(true)

    resolveMineFeeds({ ...feedPayload, feeds: [] })
    await flushPromises()

    expect(getFeeds).toHaveBeenLastCalledWith(7, true)
    secondWrapper.unmount()
  })

  it("hides my nickname from my own chat messages", async () => {
    getRoomMessages.mockResolvedValueOnce({
      ...messagePayload,
      messages: [
        {
          id: 1,
          userId: 1,
          nickname: "내 닉네임",
          content: "내가 보낸 일반 메시지",
        },
        {
          id: 2,
          userId: 2,
          nickname: "다른 참여자",
          content: "다른 사람이 보낸 메시지",
        },
        {
          id: 3,
          userId: 1,
          nickname: "내 닉네임",
          messageType: "FEED_SHARE",
          referenceFeedId: 101,
          thumbnailUrl: "",
          mediaUrl: "",
        },
        {
          id: 4,
          userId: 1,
          nickname: "내 닉네임",
          content: "내가 언급한 피드",
          referenceFeedId: 101,
        },
      ],
    })

    const wrapper = mountFeed()
    await flushPromises()

    expect(wrapper.findAll(".message-author")).toHaveLength(1)
    expect(wrapper.find(".message-author").text()).toBe("다른 참여자")
    expect(wrapper.findAll(".message.mine")).toHaveLength(3)
    expect(wrapper.findAll(".message.mine").every((message) => !message.find(".message-author").exists())).toBe(true)

    wrapper.unmount()
  })

  it("reuses the cached dashboard and keeps it visible while changing the period", async () => {
    const firstWrapper = mountMyChallenge()

    await flushPromises()

    expect(getMyChallengeDashboard).toHaveBeenCalledWith("6M")
    expect(firstWrapper.find(".profile-name").text()).toContain("절약하는 사용자")
    expect(firstWrapper.find(".total-saving").text()).toContain("500,000원")

    firstWrapper.unmount()

    const secondWrapper = mountMyChallenge()
    await flushPromises()

    expect(getMyChallengeDashboard).toHaveBeenCalledOnce()
    expect(secondWrapper.find(".dashboard-state-card").exists()).toBe(false)
    expect(secondWrapper.find(".profile-name").exists()).toBe(true)

    let resolvePeriodDashboard
    getMyChallengeDashboard.mockImplementation((period) =>
      period === "1M"
        ? new Promise((resolve) => {
            resolvePeriodDashboard = resolve
          })
        : Promise.resolve(dashboardPayload),
    )

    await secondWrapper.get(".period-select").setValue("1M")
    await nextTick()

    expect(secondWrapper.find('[role="status"]').text()).toContain("최신 내 챌린지 정보")
    expect(secondWrapper.find(".dashboard-state-card").exists()).toBe(false)
    expect(secondWrapper.find(".profile-name").text()).toContain("절약하는 사용자")

    resolvePeriodDashboard(dashboardPayload)
    await flushPromises()

    expect(getMyChallengeDashboard).toHaveBeenLastCalledWith("1M")
    secondWrapper.unmount()
  })
})
