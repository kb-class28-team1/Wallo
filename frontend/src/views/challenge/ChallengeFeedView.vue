<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"
import { useUserStore } from "@/stores/userStore"
import { getAccessToken } from "@/api/authToken"
import { refreshAccessToken } from "@/api/authApi"
import { formatWon } from "@/utils/formatters"
import AppDialog from "@/components/common/AppDialog.vue"
import AuthenticatedImage from "@/components/common/AuthenticatedImage.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppState from "@/components/ui/AppState.vue"
import { leaveChallenge as leaveChallengeRequest } from "@/api/challengeApi"
import { getTodayMissions, verifyMissionWithFeed } from "@/api/missionApi"
import {
  createFeed,
  deleteFeed,
  getAnalyzeFeedProgress,
  getFeeds,
  getRoomMessages,
  addFeedLike,
  startAnalyzeFeed,
} from "@/api/feedApi"
import {
  EXPENSE_CATEGORY_META,
  FEED_CATEGORY_CODES,
} from "@/features/financial/financialCategories"
import {
  getCachedResource,
  getResource,
  hasInFlightResource,
  invalidateResource,
} from "@/utils/resourceCache"

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const challengeId = computed(() => Number(route.params.challengeId))
const focusedFeedId = computed(() => String(route.query.focusFeedId || ""))
const feeds = ref([])
const messages = ref([])
const challengeName = ref("챌린지")
const inviteCode = ref("")
const mySavingTotal = ref(0)
const newlyCreatedFeedId = ref(null)
const activeTab = ref(String(route.query.scope || "ALL").toUpperCase() === "ME" ? "mine" : "all")
const initialLoading = ref(true)
const refreshing = ref(false)
const hasLoadedPage = ref(false)
const isLoading = computed(() => initialLoading.value || refreshing.value)
const errorMessage = ref("")
const modalOpen = ref(false)
const isAnalyzing = ref(false)
const analysisProgress = ref(0)
const analysisStageMessage = ref("분석 준비 중...")
const isGaugeTestRunning = ref(false)
const gaugeTestProgress = ref(0)
const gaugeTestStageMessage = ref("영상 분석중...")
const isUploading = ref(false)
const todayMissions = ref([])
const isMissionLoading = ref(false)
const likingFeedId = ref(null)
const likeBursts = ref([])
const pageHeartBursts = ref([])
const unmutedFeedIds = ref(new Set())
const deletingFeedId = ref(null)
const chatInput = ref("")
const chatInputElement = ref(null)
const mentionedFeed = ref(null)
const fileInput = ref(null)
const previewUrl = ref("")
const focusedFeedElement = ref(null)
const messagesElement = ref(null)
const isSendingMessage = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref("알림")
const dialogMessage = ref("")
const dialogConfirmText = ref("확인")
const dialogShowCancel = ref(false)
const isLeavingChallenge = ref(false)
let chatSocket = null
let chatReconnectTimer = null
let chatReconnectAttempts = 0
let shouldReconnectChat = true
let likeBurstSequence = 0
const likeBurstTimers = new Set()
const pageHeartMilestones = new Map()
let pageHeartCelebrationTimer = null
let newFeedAnimationTimer = null
let dialogResolver = null
let analysisRequestSequence = 0
let analysisProgressTimer = null
let gaugeTestTimer = null

const isAnalysisDisplayActive = computed(() => isAnalyzing.value || isGaugeTestRunning.value)
const analysisDisplayProgress = computed(() =>
  isGaugeTestRunning.value ? gaugeTestProgress.value : analysisProgress.value,
)
const analysisDisplayStageMessage = computed(() =>
  isGaugeTestRunning.value ? gaugeTestStageMessage.value : analysisStageMessage.value,
)

const stopAnalysisProgress = () => {
  if (analysisProgressTimer) {
    window.clearInterval(analysisProgressTimer)
    analysisProgressTimer = null
  }
}
const startAnalysisProgress = () => {
  stopAnalysisProgress()
  analysisProgressTimer = window.setInterval(() => {
    if (!isAnalyzing.value || analysisProgress.value >= 90) return
    const remaining = 90 - analysisProgress.value
    const increment = Math.max(0.2, Math.min(0.45, remaining * 0.02))
    analysisProgress.value = Math.min(90, analysisProgress.value + increment)
  }, 100)
}
const stopGaugeTest = () => {
  if (gaugeTestTimer) {
    window.clearInterval(gaugeTestTimer)
    gaugeTestTimer = null
  }
  isGaugeTestRunning.value = false
}
const getGaugeTestStageMessage = (progress) => {
  if (progress < 25) return "영상 분석중..."
  if (progress < 50) return "시세 확인중..."
  if (progress < 75) return "가격 산출중..."
  if (progress < 100) return "절약 금액 계산중..."
  return "분석 완료"
}
const openGaugeTest = () => {
  if (isAnalyzing.value) return
  stopGaugeTest()
  modalOpen.value = true
  isMissionLoading.value = false
  isGaugeTestRunning.value = true
  gaugeTestProgress.value = 0
  gaugeTestStageMessage.value = getGaugeTestStageMessage(0)
  gaugeTestTimer = window.setInterval(() => {
    if (!isGaugeTestRunning.value) return
    gaugeTestProgress.value = Math.min(100, gaugeTestProgress.value + 1.5)
    gaugeTestStageMessage.value = getGaugeTestStageMessage(gaugeTestProgress.value)
    if (gaugeTestProgress.value >= 100) {
      window.clearInterval(gaugeTestTimer)
      gaugeTestTimer = null
    }
  }, 75)
}

const FEED_STALE_TIME = 30 * 1000
const MESSAGE_STALE_TIME = 15 * 1000
const getFeedCacheKey = (id, tab) => `challenge:feeds:current:${id}:${tab}`
const getMessagesCacheKey = (id) => `challenge:messages:current:${id}`
const invalidateFeedCaches = (id = challengeId.value) => {
  invalidateResource(getFeedCacheKey(id, "all"))
  invalidateResource(getFeedCacheKey(id, "mine"))
}
const invalidatePageCaches = (id = challengeId.value) => {
  invalidateFeedCaches(id)
  invalidateResource(getMessagesCacheKey(id))
}

const form = reactive({
  file: null,
  category: "",
  caption: "",
  aiEstimatedSavingAmount: 0,
  savingAmount: 0,
  savingAmountFeedback: "",
  verifiedSavingAmount: null,
  analysisSummary: "",
  confidenceScore: 0,
  analysisDetails: "",
  analysisStatus: "IDLE",
  analysisFailed: false,
  dailyMissionId: "",
})

const spendingTypes = [
  { value: "SPENT", label: "💸 썼다" },
  { value: "REDUCED", label: "✂️ 줄였다" },
  { value: "SAVED", label: "🐷 모았다" },
]
const savingFeedbackOptions = [
  { value: "SAME", label: "같아요" },
  { value: "DIFFERENT", label: "달라요" },
  { value: "UNKNOWN", label: "몰라요" },
]
const categories = FEED_CATEGORY_CODES.map((value) => ({
  value,
  ...EXPENSE_CATEGORY_META[value],
}))
const categoryLabel = (value, custom) =>
  custom || EXPENSE_CATEGORY_META[value]?.label || value || "기타"
const spendingLabel = (value) => spendingTypes.find((item) => item.value === value)?.label || value
const isVideoFile = computed(() => form.file?.type?.startsWith("video/"))
const canConfirmSavingAmount = computed(() =>
  ["AI_COMPLETED", "AI_FAILED", "MANUAL"].includes(form.analysisStatus),
)
const analysisTitle = computed(() =>
  form.analysisStatus === "AI_FAILED" ? "✍️ 수기 입력" : "🤖 AI 추정",
)
const roomTitle = computed(() => `${challengeName.value} 채팅방`)
const DEFAULT_SPENDING_TYPE = "REDUCED"

const openDialog = ({ title = "알림", message, confirmText = "확인", showCancel = false }) =>
  new Promise((resolve) => {
    dialogTitle.value = title
    dialogMessage.value = message
    dialogConfirmText.value = confirmText
    dialogShowCancel.value = showCancel
    dialogResolver = resolve
    dialogVisible.value = true
  })

const resolveDialog = (confirmed) => {
  const resolve = dialogResolver
  dialogResolver = null
  dialogVisible.value = false
  resolve?.(confirmed)
}

const isMyFeed = (feed) => Number(feed.userId) === Number(userStore.user?.id)

// 내 게시물에서 전달한 feedId와 현재 피드의 id가 같은지 확인함.
const isFocusedFeed = (feed) => String(feed.id) === focusedFeedId.value
const getFeedCardClass = (feed) => ({
  "focused-feed": isFocusedFeed(feed),
  "new-feed-card": String(feed.id) === String(newlyCreatedFeedId.value),
})
const setFocusedFeedElement = (element, feed) => {
  if (isFocusedFeed(feed)) {
    focusedFeedElement.value = element
  }
}

// 피드 조회와 화면 렌더링이 끝난 뒤 선택한 게시물 위치로 부드럽게 이동함.
const scrollToFocusedFeed = async () => {
  if (!focusedFeedId.value || isLoading.value) {
    return
  }

  await nextTick()
  focusedFeedElement.value?.scrollIntoView({
    behavior: "smooth",
    block: "center",
  })
}

const applyFeeds = (data) => {
  feeds.value = Array.isArray(data?.feeds) ? data.feeds : []
  challengeName.value = data?.challengeName || "챌린지"
  inviteCode.value = data?.inviteCode || ""
  mySavingTotal.value = data?.mySavingTotal || 0
  return data
}

const loadFeeds = async ({ force = false } = {}) => {
  const requestedChallengeId = challengeId.value
  const requestedTab = activeTab.value
  const cacheKey = getFeedCacheKey(requestedChallengeId, requestedTab)
  const cached =
    !force && !hasInFlightResource(cacheKey)
      ? getCachedResource(cacheKey, { staleTime: FEED_STALE_TIME })
      : undefined

  if (cached !== undefined) {
    return applyFeeds(cached)
  }

  const data = await getResource(
    cacheKey,
    () => getFeeds(requestedChallengeId, requestedTab === "mine"),
    {
      force,
      staleTime: FEED_STALE_TIME,
    },
  )

  if (requestedChallengeId !== challengeId.value || requestedTab !== activeTab.value) {
    return data
  }

  return applyFeeds(data)
}
const isNearMessagesBottom = () => {
  const element = messagesElement.value
  if (!element) return true
  return element.scrollHeight - element.scrollTop - element.clientHeight <= 80
}
const scrollMessagesToBottom = async () => {
  await nextTick()
  const element = messagesElement.value
  if (element) element.scrollTop = element.scrollHeight
}
const applyMessages = (data) => {
  messages.value = Array.isArray(data?.messages) ? data.messages : []
  challengeName.value = data?.challengeName || challengeName.value
  return data
}
const loadMessages = async ({ forceScroll = false, force = false } = {}) => {
  const shouldScroll = forceScroll || isNearMessagesBottom()
  const requestedChallengeId = challengeId.value
  const cacheKey = getMessagesCacheKey(requestedChallengeId)
  const cached =
    !force && !hasInFlightResource(cacheKey)
      ? getCachedResource(cacheKey, { staleTime: MESSAGE_STALE_TIME })
      : undefined

  const data =
    cached !== undefined
      ? cached
      : await getResource(cacheKey, () => getRoomMessages(requestedChallengeId), {
          force,
          staleTime: MESSAGE_STALE_TIME,
        })

  if (requestedChallengeId !== challengeId.value) {
    return data
  }

  applyMessages(data)
  if (shouldScroll) await scrollMessagesToBottom()
  return data
}

const chatWebSocketUrl = () => {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:"
  const token = encodeURIComponent(getAccessToken() || "")
  return `${protocol}//${window.location.host}/ws/challenges/${challengeId.value}?accessToken=${token}`
}

const handleChatSocketMessage = async (event) => {
  let payload
  try {
    payload = JSON.parse(event.data)
  } catch {
    return
  }

  if (payload.type === "ERROR") {
    openDialog({ message: payload.message || "메시지를 보내지 못했습니다." })
    return
  }

  if (payload.type !== "MESSAGE" || !payload.message?.id) return
  const incomingMessage = payload.message
  if (messages.value.some((item) => Number(item.id) === Number(incomingMessage.id))) return

  const shouldScroll =
    isNearMessagesBottom() || Number(incomingMessage.userId) === Number(userStore.user?.id)
  messages.value = [...messages.value, incomingMessage]
  invalidateResource(getMessagesCacheKey(challengeId.value))
  if (shouldScroll) await scrollMessagesToBottom()
}

const scheduleChatReconnect = () => {
  if (!shouldReconnectChat || chatReconnectTimer) return
  const delay = Math.min(1_000 * 2 ** chatReconnectAttempts, 10_000)
  chatReconnectAttempts += 1
  chatReconnectTimer = window.setTimeout(() => {
    chatReconnectTimer = null
    connectChatSocket()
  }, delay)
}

const connectChatSocket = () => {
  if (!shouldReconnectChat || chatSocket) return

  chatSocket = new WebSocket(chatWebSocketUrl())
  chatSocket.onopen = () => {
    chatReconnectAttempts = 0
  }
  chatSocket.onmessage = handleChatSocketMessage
  chatSocket.onerror = () => {
    chatSocket?.close()
  }
  chatSocket.onclose = () => {
    chatSocket = null
    if (!shouldReconnectChat) return
    if (getAccessToken()) {
      scheduleChatReconnect()
      return
    }
    refreshAccessToken()
      .then(() => scheduleChatReconnect())
      .catch(() => scheduleChatReconnect())
  }
}

const disconnectChatSocket = () => {
  shouldReconnectChat = false
  if (chatReconnectTimer) {
    window.clearTimeout(chatReconnectTimer)
    chatReconnectTimer = null
  }
  if (chatSocket) {
    chatSocket.close()
    chatSocket = null
  }
}
const loadPage = async ({ force = false, forceScroll = false } = {}) => {
  const isInitialLoad = !hasLoadedPage.value
  initialLoading.value = isInitialLoad
  refreshing.value = !isInitialLoad
  errorMessage.value = ""
  try {
    await Promise.all([loadFeeds({ force }), loadMessages({ force, forceScroll })])
    hasLoadedPage.value = true
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    initialLoading.value = false
    refreshing.value = false
  }
}
const changeTab = async (tab) => {
  activeTab.value = tab
  refreshing.value = true
  errorMessage.value = ""
  try {
    await loadFeeds()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    refreshing.value = false
  }
}
const copyInviteCode = async () => {
  if (!inviteCode.value) return
  try {
    await navigator.clipboard.writeText(inviteCode.value)
    await openDialog({ message: "초대 코드가 복사되었습니다." })
  } catch {
    await openDialog({ message: `초대 코드: ${inviteCode.value}` })
  }
}

const leaveCurrentChallenge = async () => {
  if (isLeavingChallenge.value) return

  const confirmed = await openDialog({
    title: "챌린지 탈퇴",
    message: `${challengeName.value}에서 탈퇴할까요?\n탈퇴하면 이 챌린지의 피드와 채팅을 더 이상 이용할 수 없습니다.`,
    confirmText: "탈퇴하기",
    showCancel: true,
  })
  if (!confirmed) return

  isLeavingChallenge.value = true
  try {
    await leaveChallengeRequest(challengeId.value)
    invalidatePageCaches()
    disconnectChatSocket()
    await router.replace({ name: "current-challenge" })
  } catch (error) {
    openDialog({ message: error.message })
  } finally {
    isLeavingChallenge.value = false
  }
}

const openModal = async () => {
  stopGaugeTest()
  modalOpen.value = true
  isMissionLoading.value = true
  try {
    const response = await getTodayMissions()
    todayMissions.value = response.missions.filter(
      (mission) =>
        ["MEDIA_AI", "HYBRID"].includes(mission.verificationType) &&
        !mission.completed &&
        mission.status !== "VERIFYING",
    )
  } catch (error) {
    todayMissions.value = []
    await openDialog({ message: error.message })
  } finally {
    isMissionLoading.value = false
  }
}
const closeModal = () => {
  modalOpen.value = false
  stopGaugeTest()
  analysisRequestSequence += 1
  stopAnalysisProgress()
  isAnalyzing.value = false
  analysisProgress.value = 0
  analysisStageMessage.value = "분석 준비 중..."
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ""
  Object.assign(form, {
    file: null,
    category: "",
    caption: "",
    aiEstimatedSavingAmount: 0,
    savingAmount: 0,
    savingAmountFeedback: "",
    verifiedSavingAmount: null,
    analysisSummary: "",
    confidenceScore: 0,
    analysisDetails: "",
    analysisStatus: "IDLE",
    analysisFailed: false,
    dailyMissionId: "",
  })
  if (fileInput.value) fileInput.value.value = ""
}
const triggerPageHeartCelebration = (milestone) => {
  const progress = Math.min(Math.max((milestone - 50) / 950, 0), 1)
  const heartCount = Math.round(8 + progress * 20)
  const baseSize = 14 + progress * 18
  const durationMs = 1600 + Math.round(progress * 350)
  const celebrationId = Date.now()
  pageHeartBursts.value = Array.from({ length: heartCount }, (_, index) => ({
    id: `${celebrationId}-${index}`,
    left: 7 + ((index * 31) % 87),
    delay: (index * 47) % 300,
    size: Math.round(baseSize + ((index * 13) % 8)),
    drift: ((index * 53) % 81) - 40,
    rotate: ((index * 29) % 51) - 25,
    duration: durationMs,
    rise: 45 + Math.round(progress * 20),
  }))
  if (pageHeartCelebrationTimer) window.clearTimeout(pageHeartCelebrationTimer)
  pageHeartCelebrationTimer = window.setTimeout(() => {
    pageHeartBursts.value = []
    pageHeartCelebrationTimer = null
  }, durationMs + 520)
}
const addLike = async (feed) => {
  if (likingFeedId.value !== null) return
  likingFeedId.value = feed.id
  try {
    const result = await addFeedLike(challengeId.value, feed.id)
    feed.likeCount = result.likeCount
    invalidateFeedCaches()
    const likeCount = Number(feed.likeCount)
    const milestone = Math.floor(likeCount / 10) * 10
    if (
      likeCount >= 50 && likeCount <= 1000 && likeCount % 50 === 0 &&
      pageHeartMilestones.get(feed.id) !== milestone
    ) {
      pageHeartMilestones.set(feed.id, milestone)
      triggerPageHeartCelebration(milestone)
    }
    const id = ++likeBurstSequence
    likeBursts.value.push({
      id,
      feedId: feed.id,
      drift: ((id * 37) % 55) - 28,
    })
    const timer = window.setTimeout(() => {
      likeBursts.value = likeBursts.value.filter((burst) => burst.id !== id)
      likeBurstTimers.delete(timer)
    }, 950)
    likeBurstTimers.add(timer)
  } catch (error) {
    openDialog({ message: error.message })
  } finally {
    likingFeedId.value = null
  }
}
const removeFeed = async (feed) => {
  if (!isMyFeed(feed) || deletingFeedId.value !== null) return
  const confirmed = await openDialog({
    title: "피드 삭제",
    message: "이 피드를 삭제할까요?",
    confirmText: "삭제",
    showCancel: true,
  })
  if (!confirmed) return
  deletingFeedId.value = feed.id
  try {
    await deleteFeed(challengeId.value, feed.id)
    invalidatePageCaches()
    await loadPage({ force: true, forceScroll: true })
  } catch (error) {
    openDialog({ message: error.message })
  } finally {
    deletingFeedId.value = null
  }
}
const chooseFile = () => fileInput.value?.click()
const isFeedMuted = (feedId) => !unmutedFeedIds.value.has(feedId)
const toggleFeedMute = (feed) => {
  const nextUnmutedFeedIds = new Set(unmutedFeedIds.value)
  if (nextUnmutedFeedIds.has(feed.id)) {
    nextUnmutedFeedIds.delete(feed.id)
  } else {
    nextUnmutedFeedIds.add(feed.id)
  }
  unmutedFeedIds.value = nextUnmutedFeedIds
}
const playVideoPreview = (event) => {
  event.currentTarget.play().catch(() => {})
}
const handleFile = async (event) => {
  stopGaugeTest()
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith("image/") && !file.type.startsWith("video/")) {
    await openDialog({ message: "사진 또는 영상 파일을 선택해 주세요." })
    return
  }
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  form.file = file
  previewUrl.value = URL.createObjectURL(file)
  form.aiEstimatedSavingAmount = 0
  form.savingAmount = 0
  form.savingAmountFeedback = ""
  form.verifiedSavingAmount = null
  form.analysisSummary = ""
  form.analysisDetails = ""
  form.analysisStatus = "IDLE"
  form.analysisFailed = false
  stopAnalysisProgress()
  analysisProgress.value = 0
  analysisStageMessage.value = "분석 준비 중..."
}
const selectCategory = (category) => {
  stopGaugeTest()
  form.category = category
  form.aiEstimatedSavingAmount = 0
  form.savingAmount = 0
  form.savingAmountFeedback = ""
  form.verifiedSavingAmount = null
  form.analysisSummary = ""
  form.confidenceScore = 0
  form.analysisDetails = ""
  form.analysisStatus = "IDLE"
  form.analysisFailed = false
  stopAnalysisProgress()
  analysisProgress.value = 0
  analysisStageMessage.value = "분석 준비 중..."
}
const validationMessage = ({ requireCaption = false, requireAnalysis = true } = {}) => {
  if (!form.file) return "사진이나 영상을 선택해 주세요."
  if (!form.category) return "세부 카테고리를 선택해 주세요."
  if (requireAnalysis && !canConfirmSavingAmount.value) return "먼저 AI 분석을 진행해 주세요."
  if (requireAnalysis && (!Number.isFinite(form.savingAmount) || form.savingAmount < 0)) {
    return "절약 금액을 0원 이상 입력해 주세요."
  }
  if (requireCaption && !form.caption.trim()) return "한줄요약을 작성해주세요"
  return ""
}
const makeFormData = () => {
  const data = new FormData()
  data.append("media", form.file)
  data.append("spendingType", DEFAULT_SPENDING_TYPE)
  data.append("category", form.category)
  return data
}
const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))
const waitForAnalysis = async (requestSequence) => {
  const { jobId } = await startAnalyzeFeed(challengeId.value, makeFormData())
  if (!jobId) throw new Error("분석 작업을 시작하지 못했습니다.")

  while (requestSequence === analysisRequestSequence) {
    const progress = await getAnalyzeFeedProgress(challengeId.value, jobId)
    if (requestSequence !== analysisRequestSequence) return null

    analysisStageMessage.value = progress.message || "분석 중..."
    if (progress.status === "COMPLETED") {
      analysisProgress.value = 100
      return progress.result
    }
    if (progress.status === "FAILED") {
      throw new Error(progress.error || "AI 분석에 실패했습니다.")
    }
    await wait(400)
  }
  return null
}
const requestAnalysis = async () => {
  const invalid = validationMessage({ requireAnalysis: false })
  if (invalid) return openDialog({ message: invalid })
  const requestSequence = ++analysisRequestSequence
  form.analysisStatus = "ANALYZING"
  isAnalyzing.value = true
  analysisProgress.value = 0
  analysisStageMessage.value = "분석 준비 중..."
  startAnalysisProgress()
  try {
    const result = await waitForAnalysis(requestSequence)
    if (!result || requestSequence !== analysisRequestSequence) return
    form.analysisFailed = false
    form.analysisStatus = "AI_COMPLETED"
    form.aiEstimatedSavingAmount = Number(result.estimatedSavingAmount) || 0
    form.savingAmount = form.aiEstimatedSavingAmount
    form.savingAmountFeedback = ""
    form.verifiedSavingAmount = null
    form.analysisSummary = result.summary
    form.confidenceScore = result.confidenceScore
    form.analysisDetails = JSON.stringify(result)
  } catch (error) {
    if (requestSequence !== analysisRequestSequence) return
    form.analysisFailed = true
    form.analysisStatus = "AI_FAILED"
    form.aiEstimatedSavingAmount = 0
    form.savingAmount = null
    form.savingAmountFeedback = "UNKNOWN"
    form.verifiedSavingAmount = null
    form.analysisSummary = "AI 분석에 실패해 사용자가 절약 금액을 직접 입력했습니다."
    form.confidenceScore = 0
    form.analysisDetails = ""
    await openDialog({
      message: `${error.message}\n절약 금액을 직접 입력하면 피드는 계속 올릴 수 있어요.`,
    })
  } finally {
    stopAnalysisProgress()
    isAnalyzing.value = false
  }
}
const selectSavingAmountFeedback = (feedbackType) => {
  form.savingAmountFeedback = feedbackType
  if (feedbackType === "SAME") {
    form.verifiedSavingAmount = form.aiEstimatedSavingAmount
    form.savingAmount = form.aiEstimatedSavingAmount
    return
  }
  if (feedbackType === "UNKNOWN") {
    form.verifiedSavingAmount = null
    form.savingAmount = form.aiEstimatedSavingAmount
    return
  }
  form.verifiedSavingAmount = null
}
const updateVerifiedSavingAmount = () => {
  if (form.savingAmountFeedback === "DIFFERENT") {
    form.savingAmount = Math.max(0, Number(form.verifiedSavingAmount) || 0)
  }
}
const savingAmountFeedbackError = () => {
  if (!form.savingAmountFeedback) return "AI 금액과 실제 절약 금액을 확인해 주세요."
  if (
    form.savingAmountFeedback === "DIFFERENT" &&
    (form.verifiedSavingAmount === null ||
      form.verifiedSavingAmount === "" ||
      Number(form.verifiedSavingAmount) < 0)
  ) {
    return "실제 절약 금액을 입력해 주세요."
  }
  return ""
}
const uploadFeed = async () => {
  const invalid = validationMessage({ requireCaption: true })
  if (invalid) return openDialog({ message: invalid })
  if (!form.analysisSummary) return openDialog({ message: "먼저 AI 분석을 진행해 주세요." })
  if (
    form.analysisFailed &&
    (form.savingAmount === null || form.savingAmount === "" || Number(form.savingAmount) < 0)
  ) {
    return openDialog({ message: "절약 금액을 직접 입력해 주세요." })
  }
  if (!form.analysisFailed) {
    const feedbackError = savingAmountFeedbackError()
    if (feedbackError) return openDialog({ message: feedbackError })
  }
  isUploading.value = true
  try {
    const data = makeFormData()
    data.append("caption", form.caption)
    data.append("savingAmount", String(Math.max(0, Number(form.savingAmount) || 0)))
    data.append("aiEstimatedSavingAmount", String(form.aiEstimatedSavingAmount))
    data.append("savingAmountFeedback", form.savingAmountFeedback)
    if (form.verifiedSavingAmount !== null && form.verifiedSavingAmount !== "") {
      data.append("verifiedSavingAmount", String(form.verifiedSavingAmount))
    }
    data.append("analysisSummary", form.analysisSummary)
    data.append("confidenceScore", String(form.confidenceScore))
    data.append("analysisDetails", form.analysisDetails)
    const feedbackType =
      form.analysisStatus === "AI_COMPLETED"
        ? form.savingAmount === form.aiEstimatedSavingAmount
          ? "ACCEPTED"
          : "ADJUSTED"
        : "MANUAL"
    data.append("feedbackType", feedbackType)
    data.append(
      "analysisStatus",
      form.analysisStatus === "AI_FAILED" ? "AI_FAILED" : "AI_COMPLETED",
    )
    const createdFeed = await createFeed(challengeId.value, data)
    let verificationResult = null
    let verificationError = null
    if (form.dailyMissionId) {
      try {
        verificationResult = await verifyMissionWithFeed(form.dailyMissionId, createdFeed.id)
        await userStore.fetchUserProfile()
        window.dispatchEvent(new CustomEvent("wallo:mission-updated"))
      } catch (error) {
        verificationError = error
      }
    }
    closeModal()
    invalidatePageCaches()
    await loadPage({ force: true, forceScroll: true })
    newlyCreatedFeedId.value = createdFeed.id
    await nextTick()
    if (newFeedAnimationTimer) window.clearTimeout(newFeedAnimationTimer)
    newFeedAnimationTimer = window.setTimeout(() => {
      newlyCreatedFeedId.value = null
      newFeedAnimationTimer = null
    }, 700)
    if (verificationResult) {
      const resultMessage =
        verificationResult.decision === "PASS"
          ? `미션을 달성했습니다! +${verificationResult.rewardedPoint}P`
          : verificationResult.decision === "FAIL"
            ? verificationResult.reason || "미션 핵심 단어가 분석 결과에 없어 인증에 실패했습니다."
            : "AI 판단이 어려워 검토 중으로 처리했습니다."
      await openDialog({ title: "미션 인증 결과", message: resultMessage })
    } else if (verificationError) {
      await openDialog({
        title: "피드 업로드 완료",
        message: `피드는 등록됐지만 미션 인증에 실패했습니다. ${verificationError.message}`,
      })
    }
  } catch (error) {
    openDialog({ message: error.message })
  } finally {
    isUploading.value = false
  }
}
const makeMentionedFeed = (feed) => ({
  id: feed.id || feed.referenceFeedId,
  mediaUrl: feed.thumbnailUrl || feed.mediaUrl,
  mediaType: feed.mediaType,
})
const findFeedForMention = (feedId) => {
  const feed = feeds.value.find((item) => Number(item.id) === Number(feedId))
  if (feed) return feed
  return messages.value.find((item) => Number(item.referenceFeedId) === Number(feedId))
}
const setMentionedFeed = (feedId) => {
  const feed = findFeedForMention(feedId)
  if (!feed) return false
  mentionedFeed.value = makeMentionedFeed(feed)
  return true
}
const handleChatInput = () => {
  const mention = chatInput.value.match(/@(피드)?(\d+)/i)
  if (mention && setMentionedFeed(mention[2])) {
    chatInput.value = chatInput.value
      .replace(mention[0], "")
      .replace(/\s{2,}/g, " ")
      .trimStart()
  }
  resizeChatInput()
}
const resizeChatInput = () => {
  const element = chatInputElement.value
  if (!element) return
  const maxHeight = 120
  element.style.height = "auto"
  element.style.height = `${Math.min(element.scrollHeight, maxHeight)}px`
  element.style.overflowY = element.scrollHeight > maxHeight ? "auto" : "hidden"
}
const mentionFeed = (message) => {
  setMentionedFeed(message.referenceFeedId)
  chatInput.value = ""
  void nextTick(resizeChatInput)
}
const isFeedShareMessage = (message) =>
  String(message?.messageType || "").toUpperCase() === "FEED_SHARE"
const isFeedMentionMessage = (message) =>
  Boolean(message?.referenceFeedId) && !isFeedShareMessage(message)
const isMyMessage = (message) => Number(message?.userId) === Number(userStore.user?.id)
const mentionFeedFromCard = async (feed) => {
  mentionedFeed.value = makeMentionedFeed(feed)
  chatInput.value = ""
  await nextTick()
  resizeChatInput()
  chatInputElement.value?.focus()
}
const sendMessage = async () => {
  const content = chatInput.value.trim()
  if ((!content && !mentionedFeed.value) || isSendingMessage.value) return
  if (!chatSocket || chatSocket.readyState !== WebSocket.OPEN) {
    openDialog({ message: "채팅 서버에 연결 중입니다. 잠시 후 다시 시도해 주세요." })
    return
  }
  isSendingMessage.value = true
  try {
    chatSocket.send(
      JSON.stringify({
        content: content || null,
        referenceFeedId: mentionedFeed.value?.id || null,
      }),
    )
    invalidateResource(getMessagesCacheKey(challengeId.value))
    chatInput.value = ""
    mentionedFeed.value = null
    await nextTick()
    resizeChatInput()
    await scrollMessagesToBottom()
  } catch (error) {
    openDialog({ message: error.message || "메시지를 보내지 못했습니다." })
  } finally {
    isSendingMessage.value = false
    await nextTick()
    chatInputElement.value?.focus()
  }
}

watch([focusedFeedId, feeds, isLoading], scrollToFocusedFeed, { flush: "post" })

onMounted(async () => {
  await loadPage()
  await scrollMessagesToBottom()
  connectChatSocket()
})
onBeforeUnmount(() => {
  disconnectChatSocket()
  analysisRequestSequence += 1
  stopAnalysisProgress()
  stopGaugeTest()
  likeBurstTimers.forEach((timer) => window.clearTimeout(timer))
  if (pageHeartCelebrationTimer) window.clearTimeout(pageHeartCelebrationTimer)
  if (newFeedAnimationTimer) window.clearTimeout(newFeedAnimationTimer)
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <section class="feed-page">
    <div v-if="pageHeartBursts.length" class="page-heart-celebration" aria-hidden="true">
      <span
        v-for="heart in pageHeartBursts"
        :key="heart.id"
        class="page-heart"
        :style="{
          '--heart-left': `${heart.left}%`,
          '--heart-delay': `${heart.delay}ms`,
          '--heart-size': `${heart.size}px`,
          '--heart-drift': `${heart.drift}px`,
          '--heart-rotate': `${heart.rotate}deg`,
          '--heart-duration': `${heart.duration}ms`,
          '--heart-rise': `${heart.rise}vh`,
        }"
      >
        <i class="bi bi-heart-fill" aria-hidden="true"></i>
      </span>
    </div>
    <AppAlert
      v-if="refreshing"
      class="feed-refresh-status"
      variant="neutral"
      role="status"
      :show-icon="false"
      message="최신 피드와 채팅을 확인하는 중..."
    />
    <AppAlert v-if="errorMessage && hasLoadedPage" class="feed-error-alert" variant="warning">
      <div class="feed-alert-content">
        <span>{{ errorMessage }}</span>
        <AppButton variant="outline" size="sm" @click="loadPage({ force: true })">
          다시 시도
        </AppButton>
      </div>
    </AppAlert>
    <AppState
      v-if="initialLoading"
      class="page-state"
      type="loading"
      title="챌린지 피드를 불러오는 중입니다"
      message="잠시만 기다려 주세요."
    />
    <AppState
      v-else-if="errorMessage && !hasLoadedPage"
      class="page-state"
      type="error"
      title="챌린지 피드를 불러오지 못했습니다"
      :message="errorMessage"
      action-text="다시 시도"
      action-variant="danger"
      @action="loadPage({ force: true })"
    />
    <template v-else>
      <header class="feed-header">
        <div class="feed-header-top">
          <div class="feed-title-group">
            <h1 class="feed-challenge-name">{{ challengeName }}</h1>
            <div v-if="inviteCode" class="feed-header-actions">
              <div class="feed-invite-panel">
                <AppButton
                  variant="outline"
                  size="sm"
                  aria-label="초대 코드 복사"
                  @click="copyInviteCode"
                >
                  <template #leading>
                    <i class="bi bi-copy" aria-hidden="true"></i>
                  </template>
                  초대코드 복사
                </AppButton>
              </div>
            </div>
          </div>
          <div class="saving-total">
            <small>누적 절약 금액</small><strong>{{ formatWon(mySavingTotal) }}</strong>
          </div>
        </div>
        <div class="feed-header-bottom">
          <nav class="feed-tabs">
            <AppButton
              variant="ghost"
              size="sm"
              :class="{ active: activeTab === 'all' }"
              @click="changeTab('all')"
            >
              전체 피드
            </AppButton>
            <AppButton
              variant="ghost"
              size="sm"
              :class="{ active: activeTab === 'mine' }"
              @click="changeTab('mine')"
            >
              내 피드
            </AppButton>
          </nav>
        </div>
      </header>

      <div class="feed-layout">
        <main class="feed-column">
          <div v-if="!feeds.length" class="empty-feed">
            <span>📷</span><strong>아직 등록된 피드가 없어요</strong>
            <p>오른쪽 아래 + 버튼을 눌러 첫 절약 기록을 남겨보세요.</p>
          </div>
          <article
            v-for="feed in feeds"
            :key="feed.id"
            :ref="(element) => setFocusedFeedElement(element, feed)"
            class="feed-card"
            :class="getFeedCardClass(feed)"
            :data-feed-id="feed.id"
          >
            <span v-if="isFocusedFeed(feed)" class="focus-badge">선택한 게시물</span>
            <header>
              <AuthenticatedImage :src="feed.profileImageUrl" alt="" />
              <div>
                <strong>{{ feed.nickname }}</strong
                ><span class="d-none"> >{{ spendingLabel(feed.spendingType) }} · > </span>
                <small>{{ categoryLabel(feed.category, feed.customCategory) }}</small>
              </div>
              <span class="saving-badge">+ {{ formatWon(feed.savingAmount) }}</span>
            </header>
            <div class="feed-media-wrap">
              <video
                v-if="feed.mediaType === 'VIDEO'"
                class="feed-media"
                :src="feed.mediaUrl"
                autoplay
                :muted="isFeedMuted(feed.id)"
                loop
                playsinline
                preload="metadata"
                :aria-label="feed.caption || '절약 인증 영상'"
              ></video>
              <button
                v-if="feed.mediaType === 'VIDEO'"
                type="button"
                class="feed-sound-toggle"
                :aria-label="isFeedMuted(feed.id) ? '소리 켜기' : '소리 끄기'"
                @click.stop="toggleFeedMute(feed)"
              >
                <i
                  :class="[
                    'bi',
                    isFeedMuted(feed.id) ? 'bi-volume-mute-fill' : 'bi-volume-up-fill',
                  ]"
                  aria-hidden="true"
                ></i>
              </button>
              <img
                v-else
                class="feed-media"
                :src="feed.mediaUrl"
                :alt="feed.caption || '절약 인증 사진'"
              />
              <div class="like-burst-layer" aria-hidden="true">
                <span
                  v-for="burst in likeBursts.filter((item) => item.feedId === feed.id)"
                  :key="burst.id"
                  class="like-burst"
                  :style="{ '--like-drift': `${burst.drift}px` }"
                >
                  <i class="bi bi-heart-fill" aria-hidden="true"></i>
                </span>
              </div>
              <div class="feed-like-row">
                <button
                  type="button"
                  class="mention-feed-button"
                  aria-label="언급하기"
                  title="언급하기"
                  @click.stop="mentionFeedFromCard(feed)"
                >
                  <i class="bi bi-send" aria-hidden="true"></i>
                  언급하기
                </button>
                <button
                  type="button"
                  class="like-button"
                  :disabled="likingFeedId === feed.id"
                  aria-label="좋아요 추가"
                  @click.stop="addLike(feed)"
                >
                  <span class="like-icon" aria-hidden="true">
                    <i class="bi bi-heart-fill"></i>
                  </span>
                  <strong>{{ feed.likeCount || 0 }}</strong>
                </button>
              </div>
            </div>
            <footer>
              <div class="feed-caption-row">
                <p>{{ feed.caption || "오늘의 절약 기록을 공유했어요." }}</p>
                <div v-if="isMyFeed(feed)" class="feed-owner-actions">
                  <button
                    type="button"
                    :disabled="deletingFeedId === feed.id"
                    @click.stop="removeFeed(feed)"
                  >
                    {{ deletingFeedId === feed.id ? "삭제 중..." : "삭제" }}
                  </button>
                </div>
              </div>
            </footer>
          </article>
        </main>

        <aside class="feed-sidebar">
          <section class="chat-room">
            <header>
              <div class="chat-room-heading">
                <span class="online-dot"></span>
                <h2>{{ roomTitle }}</h2>
              </div>
              <AppButton
                class="feed-leave-button"
                variant="ghost"
                size="sm"
                title="챌린지 나가기"
                aria-label="챌린지 나가기"
                :disabled="isLeavingChallenge"
                @click="leaveCurrentChallenge"
              >
                나가기
              </AppButton>
            </header>
            <div ref="messagesElement" class="messages">
              <div
                v-for="item in messages"
                :key="item.id"
                class="message"
                :class="{ mine: isMyMessage(item) }"
              >
                <div v-if="isFeedShareMessage(item)" class="feed-share-message">
                  <strong class="message-author">{{ item.nickname }}</strong>
                  <div class="feed-attachment">
                    <small class="feed-attachment-label">피드 #{{ item.referenceFeedId }}</small>
                    <button type="button" class="shared-feed" @click="mentionFeed(item)">
                      <video
                        v-if="item.mediaType === 'VIDEO'"
                        :src="item.mediaUrl"
                        autoplay
                        muted
                        loop
                        playsinline
                        preload="metadata"
                        aria-label="공유된 피드 영상"
                      ></video>
                      <img
                        v-else
                        :src="item.thumbnailUrl || item.mediaUrl"
                        alt="공유된 피드 썸네일"
                      />
                    </button>
                  </div>
                </div>
                <template v-else-if="isFeedMentionMessage(item)">
                  <strong class="message-author">{{ item.nickname }}</strong>
                  <div class="feed-mention">
                    <div class="feed-attachment">
                      <small class="feed-attachment-label">피드 #{{ item.referenceFeedId }}</small>
                      <button type="button" class="shared-feed" @click="mentionFeed(item)">
                        <video
                          v-if="item.mediaType === 'VIDEO'"
                          :src="item.mediaUrl"
                          autoplay
                          muted
                          loop
                          playsinline
                          preload="metadata"
                          aria-label="첨부된 피드 영상"
                        ></video>
                        <img
                          v-else
                          :src="item.thumbnailUrl || item.mediaUrl"
                          alt="첨부된 피드 썸네일"
                        />
                      </button>
                    </div>
                    <p v-if="item.content" class="message-bubble">{{ item.content }}</p>
                  </div>
                </template>
                <template v-else>
                  <div class="message-content">
                    <strong class="message-author">{{ item.nickname }}</strong>
                    <p v-if="item.content" class="message-bubble">{{ item.content }}</p>
                  </div>
                </template>
              </div>
            </div>
            <div v-if="mentionedFeed" class="mention-preview">
              <span>피드 #{{ mentionedFeed.id }} 언급 중</span>
              <button @click="mentionedFeed = null">×</button>
            </div>
            <form class="chat-form" @submit.prevent="sendMessage">
              <textarea
                ref="chatInputElement"
                v-model="chatInput"
                rows="1"
                placeholder="메시지 보내기..."
                :disabled="isSendingMessage"
                @input="handleChatInput"
                @keydown.enter.exact.prevent
                @keyup.enter.exact.prevent="sendMessage"
              ></textarea>
              <button type="submit" aria-label="메시지 전송" :disabled="isSendingMessage">
                <i class="bi bi-send" aria-hidden="true"></i>
              </button>
            </form>
          </section>
        </aside>
      </div>
      <div class="floating-actions">
        <AppButton
          class="floating-gauge-test"
          variant="secondary"
          aria-label="분석 게이지 테스트"
          title="분석 게이지 테스트"
          @click="openGaugeTest"
        >
          🌊
        </AppButton>
        <AppButton
          class="floating-add"
          variant="primary"
          size="lg"
          aria-label="절약 피드 추가"
          @click="openModal"
        >
          +
        </AppButton>
      </div>
    </template>

    <div v-if="modalOpen" class="modal-layer" @click.self="closeModal">
      <section class="upload-modal" role="dialog" aria-modal="true" aria-labelledby="upload-title">
        <header>
          <h2 id="upload-title">절약 피드 추가</h2>
          <button @click="closeModal">×</button>
        </header>
        <div class="modal-body">
          <label class="section-label">인증 자료</label>
          <input
            ref="fileInput"
            class="visually-hidden"
            type="file"
            accept="image/*,video/*"
            @change="handleFile"
          />
          <div
            class="upload-zone"
            :class="{ 'has-preview': previewUrl }"
            role="button"
            tabindex="0"
            @click="chooseFile"
            @keydown.enter.prevent="chooseFile"
            @keydown.space.prevent="chooseFile"
          >
            <template v-if="previewUrl">
              <video
                v-if="isVideoFile"
                :key="previewUrl"
                :src="previewUrl"
                autoplay
                muted
                loop
                playsinline
                preload="auto"
                @click.stop
                @loadeddata="playVideoPreview"
                aria-label="업로드할 영상 미리보기"
              ></video>
              <img v-else :src="previewUrl" alt="업로드 미리보기" />
            </template>
            <template v-else
              ><span>🖼️</span><strong>사진 / 동영상 업로드</strong
              ><small>클릭해 인증 사진 또는 영상을 올려주세요</small></template
            >
          </div>

          <label class="section-label">세부 카테고리</label>
          <div class="chip-row">
            <button
              v-for="item in categories"
              :key="item.value"
              type="button"
              :class="{ selected: form.category === item.value }"
              @click="selectCategory(item.value)"
            >
              <i :class="['bi', item.icon]" aria-hidden="true"></i>
              {{ item.label }}
            </button>
          </div>

          <label class="section-label" for="mission-selection">오늘의 미션 인증 (선택)</label>
          <select
            id="mission-selection"
            v-model="form.dailyMissionId"
            class="form-select mission-selection"
            :disabled="isMissionLoading"
          >
            <option value="">
              {{ isMissionLoading ? "미션을 불러오는 중..." : "일반 피드로 등록" }}
            </option>
            <option
              v-for="mission in todayMissions"
              :key="mission.dailyMissionId"
              :value="mission.dailyMissionId"
            >
              {{ mission.title }} (+{{ mission.rewardPoint }}P)
            </option>
          </select>
          <small v-if="!isMissionLoading && !todayMissions.length" class="mission-selection-help">
            현재 영상으로 인증할 수 있는 오늘의 미션이 없습니다.
          </small>

          <div class="analysis-box">
            <div>
              <b>🤖 AI 분석</b><span>선택 정보와 미디어를 외부 AI 분석기로 전달합니다.</span>
            </div>
            <button
              type="button"
              :class="{ 'is-analyzing': isAnalysisDisplayActive }"
              :style="
                isAnalysisDisplayActive
                  ? { '--analysis-progress': `${analysisDisplayProgress}%` }
                  : undefined
              "
              :disabled="isAnalyzing || isGaugeTestRunning"
              @click="requestAnalysis"
            >
              <span v-if="isAnalysisDisplayActive" class="analysis-ocean-fill" aria-hidden="true"></span>
              <span v-if="isAnalysisDisplayActive" class="analysis-surf-wave-loader" aria-hidden="true">
                <img
                  class="analysis-surf-wave-art"
                  src="/assets/G_penguin_C_wave_surf.webp"
                  alt=""
                />
                <i class="analysis-surf-wave-ripple ripple-one"></i>
                <i class="analysis-surf-wave-ripple ripple-two"></i>
                <i class="analysis-surf-wave-ripple ripple-three"></i>
              </span>
              <img
                v-if="isAnalysisDisplayActive"
                class="analysis-penguin-loader"
                src="/assets/G_penguin_C_wave_surf.webp"
                alt=""
                aria-hidden="true"
              />
              <span class="analysis-button-label">
                {{ isAnalysisDisplayActive ? analysisDisplayStageMessage : "✨ AI에게 분석 맡기기" }}
              </span>
            </button>
            <div v-if="isAnalysisDisplayActive" class="analysis-progress-label" aria-live="polite">
              분석 진행률 {{ Math.round(analysisDisplayProgress) }}%
            </div>
          </div>
          <div class="result-box" :class="{ ready: form.analysisSummary }">
            <span>{{ form.analysisFailed ? "✍️ 직접 입력 금액" : "🤖 AI 추정 금액" }}</span
            ><small>{{ form.analysisSummary || "분석하면 예상 절약 금액을 알려드려요." }}</small>
            <div>
              <input
                v-if="form.analysisFailed"
                v-model.number="form.savingAmount"
                type="number"
                min="0"
                placeholder="절약한 금액을 입력하세요"
              />
              <input
                v-else
                :value="form.aiEstimatedSavingAmount"
                type="number"
                min="0"
                readonly
                :disabled="!form.analysisSummary"
              /><b>원</b>
            </div>
            <div
              v-if="form.analysisSummary && !form.analysisFailed"
              class="saving-feedback-section"
            >
              <strong>실제 금액과 같나요?</strong>
              <div class="saving-feedback-buttons">
                <button
                  v-for="option in savingFeedbackOptions"
                  :key="option.value"
                  type="button"
                  :class="{ selected: form.savingAmountFeedback === option.value }"
                  @click="selectSavingAmountFeedback(option.value)"
                >
                  {{ option.label }}
                </button>
              </div>
              <div v-if="form.savingAmountFeedback === 'DIFFERENT'" class="verified-amount-row">
                <label for="verified-saving-amount">실제 절약 금액</label>
                <div>
                  <input
                    id="verified-saving-amount"
                    v-model.number="form.verifiedSavingAmount"
                    type="number"
                    min="0"
                    @input="updateVerifiedSavingAmount"
                  /><b>원</b>
                </div>
              </div>
              <small v-if="form.savingAmountFeedback === 'UNKNOWN'" class="feedback-help">
                AI 추정 금액을 그대로 저장하지만 보정 학습에는 사용하지 않아요.
              </small>
              <small v-else-if="form.savingAmountFeedback === 'DIFFERENT'" class="feedback-help">
                입력한 실제 금액이 피드에 저장되고 다음 분석의 보정 자료로 사용돼요.
              </small>
            </div>
          </div>
          <label class="section-label" for="feed-caption">한줄요약 (필수)</label>
          <textarea
            id="feed-caption"
            v-model="form.caption"
            maxlength="500"
            required
            aria-required="true"
            placeholder="예) 퇴근길 편의점 대신 집에서 커피 ☕ 굿!"
          ></textarea>
          <p class="share-notice">💬 업로드하면 {{ roomTitle }}에도 자동으로 공유돼요.</p>
        </div>
        <footer>
          <button class="cancel" @click="closeModal">취소</button
          ><button class="submit" :disabled="isUploading" @click="uploadFeed">
            {{ isUploading ? "올리는 중..." : "피드 올리기" }}
          </button>
        </footer>
      </section>
    </div>

    <AppDialog
      :visible="dialogVisible"
      :title="dialogTitle"
      :message="dialogMessage"
      :confirm-text="dialogConfirmText"
      :show-cancel="dialogShowCancel"
      @confirm="resolveDialog(true)"
      @close="resolveDialog(false)"
    />
  </section>
</template>

<style scoped>
.feed-page {
  min-height: calc(100vh - 124px);
  position: relative;
  color: #202840;
}
.page-state {
  min-height: 520px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 18px;
}
.feed-refresh-status,
.feed-error-alert {
  margin-bottom: 18px;
}
.feed-alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.feed-header {
  width: calc(100% - 352px);
  margin-bottom: 24px;
}
.feed-header :deep(.app-page-header__title) {
  justify-self: start;
  min-width: 0;
  max-width: 100%;
  margin: 8px 0 4px;
  overflow: hidden;
  color: inherit;
  font-size: 2rem;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.feed-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.feed-header :deep(.app-page-header__description) {
  margin: 8px 0 0;
  color: #939bad;
  font-size: 0.67rem;
}
.feed-leave-button.app-button {
  display: inline-flex !important;
  visibility: visible !important;
  position: static;
  width: 44px;
  height: 44px;
  align-items: center;
  justify-content: center;
  padding: 0;
  color: #ff6b6b;
  background: transparent;
  border: 0;
  border-radius: 50%;
  font-size: 2rem;
  line-height: 1;
  transition:
    color 0.2s ease,
    background 0.2s ease,
    border-color 0.2s ease;
}
.feed-leave-button :deep(.app-button__label) {
  display: none;
}
.feed-leave-button :deep(.bi-door-open-fill) {
  display: none;
}
.feed-leave-button.app-button:hover:not(:disabled) {
  color: #ff6b6b;
  background: transparent;
}
.feed-leave-button.app-button:hover:not(:disabled) :deep(.bi-door-open) {
  display: none;
}
.feed-leave-button.app-button:hover:not(:disabled) :deep(.bi-door-open-fill) {
  display: inline-block;
}
.feed-leave-button:disabled {
  cursor: wait;
  opacity: 0.6;
}
.saving-total {
  width: max-content;
  box-sizing: border-box;
  padding: 0;
  background: transparent;
  border-radius: 0;
}
.saving-total small,
.saving-total strong {
  display: block;
}
.saving-total small {
  color: #8e87ba;
}
.saving-total strong {
  margin-top: 4px;
  color: #6758d6;
  font-size: 1.35rem;
}
.feed-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 330px);
  gap: 22px;
  align-items: start;
}
.feed-column {
  min-width: 0;
}
.feed-sidebar {
  position: fixed;
  top: 100px;
  right: max(32px, calc((100vw - 1453px) / 2));
  display: flex;
  width: 330px;
  height: calc(100vh - 124px);
  min-width: 0;
  flex-direction: column;
  gap: 18px;
}
.feed-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}
.feed-tabs {
  display: flex;
  gap: 2px;
  padding: 3px;
  background: #f0eff7;
  border-radius: 9px;
  width: max-content;
}
.feed-tabs button {
  padding: 5px 10px;
  border: 0;
  border-radius: 7px;
  color: #8b91a3;
  background: transparent;
  font-size: 0.75rem;
  font-weight: 800;
}
.feed-tabs button.active {
  color: #fff;
  background: #6f61dc;
}
.feed-invite-panel {
  display: flex;
  align-items: center;
  min-width: 0;
}
.feed-invite-panel button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: auto;
  height: 36px;
  padding: 0 12px;
  color: #6f61dc;
  background: #f0edff;
  border: 1px solid #dcd6ff;
  border-radius: 10px;
  font-size: calc(0.78rem + 1px);
  font-weight: 800;
  white-space: nowrap;
}
.empty-feed {
  display: grid;
  place-items: center;
  min-height: 430px;
  background: #fff;
  border: 1px dashed #d9dbe8;
  border-radius: 22px;
  text-align: center;
}
.empty-feed span {
  font-size: 2.4rem;
}
.empty-feed strong {
  margin-top: -80px;
}
.empty-feed p {
  margin-top: -100px;
  color: #999fb0;
}
.feed-card {
  position: relative;
  overflow: hidden;
  margin-bottom: 18px;
  border: 2px solid transparent;
  background: #121d3e;
  border-radius: 22px;
  box-shadow: 0 14px 32px #29315a1f;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}
.feed-card.new-feed-card {
  animation: feed-card-enter 650ms cubic-bezier(0.22, 1, 0.36, 1) both;
}
.feed-card.focused-feed {
  border-color: #8d80ff;
  box-shadow:
    0 0 0 5px #8d80ff2e,
    0 18px 38px #29315a35;
  transform: translateY(-2px);
  animation: focus-pulse 900ms ease-out;
}
.focus-badge {
  position: absolute;
  top: 12px;
  right: 16px;
  z-index: 2;
  padding: 5px 10px;
  color: #fff;
  background: #796bea;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 850;
}
.feed-card header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  color: #fff;
}
.feed-card header img {
  width: 42px;
  height: 42px;
  object-fit: cover;
  border-radius: 50%;
  background: #fff;
}
.feed-card header div {
  flex: 1;
}
.feed-card header strong,
.feed-card header small {
  display: block;
}
.feed-card header small {
  color: #aeb8d4;
}
.saving-badge {
  padding: 7px 11px;
  color: #dcd7ff;
  background: #ffffff18;
  border-radius: 999px;
  font-size: 0.82rem;
  font-weight: 800;
}
.feed-media-wrap {
  position: relative;
  background: #eaf4ff;
}
.feed-media,
.feed-media-wrap > video {
  display: block;
  width: 100%;
  max-height: 560px;
  object-fit: contain;
  background: #eaf4ff;
}
.feed-like-row {
  position: absolute;
  left: 18px;
  bottom: 14px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 10px;
}
.feed-sound-toggle {
  position: absolute;
  right: 18px;
  bottom: 70px;
  z-index: 4;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  color: #fff;
  background: #08122dcc;
  border: 1px solid #ffffff55;
  border-radius: 50%;
  font-size: 1rem;
}
.feed-sound-toggle:hover {
  background: #7162de;
}
.like-burst-layer {
  position: absolute;
  inset: 0;
  z-index: 5;
  overflow: hidden;
  pointer-events: none;
}
.like-burst {
  position: absolute;
  left: 24px;
  bottom: 28px;
  display: inline-grid;
  width: 1.25rem;
  height: 1.25rem;
  place-items: center;
  color: #ff6387;
  font-size: 1.15rem;
  line-height: 1;
  opacity: 0;
  text-shadow: 0 3px 12px #ff638766;
  animation: like-heart-rise 950ms ease-out forwards;
}
.page-heart-celebration {
  position: fixed;
  inset: 0;
  z-index: 1000;
  overflow: hidden;
  pointer-events: none;
}
.page-heart {
  position: absolute;
  left: var(--heart-left);
  bottom: 24%;
  color: #ff6387;
  font-size: var(--heart-size);
  line-height: 1;
  opacity: 0;
  text-shadow: 0 2px 7px #ff638744;
  animation: page-heart-pop var(--heart-duration) ease-out var(--heart-delay) forwards;
}
.page-heart i {
  display: block;
  font-style: normal;
}
.like-button {
  order: 1;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  color: #ffe36e;
  background: transparent;
  border: 0;
  border-radius: 0;
  font-weight: 800;
}
.like-button span {
  color: #ff9eb5;
  font-size: 1.5rem;
  line-height: 1;
}
.like-button strong {
  color: #ff9eb5;
  font-size: 0.9rem;
  line-height: 1;
}
.like-button:disabled {
  cursor: wait;
  opacity: 0.6;
}
.mention-feed-button {
  order: 2;
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  transform: translateX(4px);
  color: #fff;
  background: transparent;
  border: 0;
  border-radius: 0;
  font-size: 0;
}
.mention-feed-button i {
  display: inline-block;
  font-size: 18px;
  line-height: 1;
  transform: translateY(2px);
}
.mention-feed-button:hover {
  color: #dcd7ff;
  background: transparent;
}
@keyframes like-heart-rise {
  0% {
    opacity: 0;
    transform: translate3d(0, 12px, 0) scale(0.65) rotate(-10deg);
  }
  16% {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translate3d(var(--like-drift), -150px, 0) scale(1.05) rotate(12deg);
  }
}
@keyframes page-heart-pop {
  0% {
    opacity: 0;
    transform: translate3d(0, 18px, 0) scale(0.45) rotate(0deg);
  }
  18% {
    opacity: 0.72;
    transform: translate3d(0, 0, 0) scale(0.88) rotate(0deg);
  }
  100% {
    opacity: 0;
    transform: translate3d(
        var(--heart-drift),
        calc(var(--heart-rise) * -1),
        0
      )
      scale(0.72)
      rotate(var(--heart-rotate));
  }
}
.feed-card footer {
  position: relative;
  z-index: 2;
  margin-top: -56px;
  padding: 49px 18px 18px;
  color: #fff;
  background: #121d3e;
}
.feed-caption-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
.feed-card footer p {
  min-width: 0;
  flex: 1;
  margin: 0 0 8px;
  font-weight: 700;
}
.feed-owner-actions {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
}
.feed-owner-actions button {
  padding: 5px 8px;
  color: #aeb8d4;
  background: transparent;
  border: 1px solid #ffffff25;
  border-radius: 7px;
  font-size: 0.75rem;
}
.feed-owner-actions button:last-child {
  color: #ffb5c4;
  border-color: #ff9fb544;
}
.feed-owner-actions button:disabled {
  opacity: 0.5;
}
.feed-card footer span {
  color: #aeb8d4;
  font-size: 0.8rem;
}
.chat-room {
  display: flex;
  flex-direction: column;
  flex: 1;
  height: auto;
  min-height: 0;
  overflow: hidden;
  color: #e7eaff;
  background: #111a36;
  border-radius: 22px;
}
.chat-room > header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px;
  border-bottom: 1px solid #ffffff14;
}
.chat-room h2 {
  margin: 0;
  font-size: 1rem;
}
.chat-room header small {
  color: #8d96b7;
}
.online-dot {
  width: 9px;
  height: 9px;
  background: #65d49a;
  border-radius: 50%;
}
.messages {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
  scrollbar-color: #4c587b transparent;
  scrollbar-width: thin;
}
.messages::-webkit-scrollbar {
  width: 8px;
}
.messages::-webkit-scrollbar-track {
  background: transparent;
}
.messages::-webkit-scrollbar-thumb {
  background: #4c587b;
  border: 2px solid transparent;
  border-radius: 999px;
  background-clip: padding-box;
}
.message {
  margin-bottom: 14px;
}
.feed-share-message {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.message.mine .feed-share-message {
  align-items: flex-end;
}
.message-author {
  display: block;
  margin-bottom: 4px;
  color: #8bdfbc;
  font-size: 0.75rem;
}
.message.mine .message-author {
  color: #f6cf75;
}
.message.mine > .message-author {
  text-align: right;
}
.feed-mention {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.message.mine .feed-mention {
  align-items: flex-end;
}
.feed-attachment {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
.message.mine .feed-attachment {
  align-items: flex-end;
}
.feed-attachment-label {
  color: #aeb8d4;
  font-size: 0.7rem;
  font-weight: 700;
}
.message-content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.message.mine .message-content {
  align-items: flex-end;
}
.message-bubble {
  width: fit-content;
  max-width: 100%;
  padding: 8px 12px;
  color: #f1f2ff;
  background: #2b385e;
  border-radius: 13px 13px 13px 4px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.message.mine .message-bubble {
  margin-left: auto;
  background: #7162de;
  border-radius: 13px 13px 4px 13px;
}
.message p {
  margin: 5px 0;
  font-size: 0.86rem;
  line-height: 1.45;
}
.shared-feed {
  display: flex;
  width: min(100%, 190px);
  padding: 0;
  overflow: hidden;
  color: #fff;
  text-align: left;
  background: #202b4d;
  border: 1px solid #ffffff12;
  border-radius: 12px;
  cursor: pointer;
}
.shared-feed img,
.shared-feed video {
  display: block;
  width: 190px;
  height: 140px;
  object-fit: cover;
}
.mention-preview {
  display: flex;
  justify-content: space-between;
  padding: 8px 14px;
  color: #c7c1ff;
  background: #27224c;
  font-size: 0.76rem;
}
.mention-preview button {
  color: #fff;
  background: transparent;
  border: 0;
}
.chat-form {
  display: flex;
  align-items: flex-end;
  gap: 7px;
  padding: 12px;
  background: #172140;
}
.chat-form textarea {
  min-width: 0;
  flex: 1;
  min-height: 40px;
  max-height: 120px;
  padding: 11px 13px;
  color: #fff;
  background: #222d4d;
  border: 0;
  border-radius: 12px;
  outline: 0;
  resize: none;
  overflow-y: hidden;
  font: inherit;
  line-height: 1.4;
}
.chat-form button,
.floating-add {
  display: grid;
  place-items: center;
  color: #fff;
  background: #7162de;
  border: 0;
  border-radius: 50%;
  font-weight: 800;
}
.chat-form button {
  width: 40px;
  height: 40px;
  font-size: 1.05rem;
}
.chat-form button:disabled {
  cursor: wait;
  opacity: 0.55;
}
.floating-add {
  position: fixed;
  right: 34px;
  bottom: 30px;
  z-index: 40;
  width: 58px;
  height: 58px;
  min-height: 0;
  padding: 0;
  font-size: 2rem;
  box-shadow: 0 10px 28px #6658cf66;
}
.floating-actions {
  position: fixed;
  right: 34px;
  bottom: 30px;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 10px;
}
.floating-actions .floating-add {
  position: static;
}
.floating-gauge-test {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  min-height: 0;
  padding: 0;
  color: #4f72cb;
  background: #eaf2ff;
  border: 1px solid #cddcf7;
  border-radius: 50%;
  font-size: 1.15rem;
  box-shadow: 0 8px 18px rgb(88 117 170 / 16%);
}
.floating-gauge-test:hover:not(:disabled) {
  color: #385db8;
  background: #dbe9ff;
}
.modal-layer {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: grid;
  place-items: center;
  padding: 20px;
  background: #13172b99;
  backdrop-filter: blur(3px);
}
.upload-modal {
  display: flex;
  flex-direction: column;
  width: min(600px, 100%);
  max-height: 92vh;
  overflow: hidden;
  background: #fff;
  border-radius: 26px;
  box-shadow: 0 25px 80px #0004;
}
.upload-modal > header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 28px 8px;
}
.upload-modal h2 {
  margin: 0;
  font-size: 1.4rem;
  font-weight: 900;
}
.upload-modal > header button {
  font-size: 2rem;
  color: #9ba2b7;
  background: transparent;
  border: 0;
}
.modal-body {
  overflow: auto;
  padding: 12px 28px 20px;
}
.section-label {
  display: block;
  margin: 16px 0 9px;
  font-size: 0.88rem;
  font-weight: 850;
}
.upload-zone {
  display: grid;
  place-items: center;
  width: 100%;
  min-height: 170px;
  height: min(360px, 42vh);
  padding: 10px;
  overflow: hidden;
  background: #fafbfe;
  border: 2px dashed #d9dcec;
  border-radius: 18px;
}
.upload-zone.has-preview {
  height: auto;
  min-height: 170px;
  background: #f4f5fa;
}
.upload-zone > span {
  font-size: 2rem;
}
.upload-zone strong,
.upload-zone small {
  display: block;
}
.upload-zone small {
  color: #9ba2b5;
}
.upload-zone img,
.upload-zone video {
  display: block;
  max-width: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 10px;
}
.upload-zone img {
  width: 100%;
  max-height: none;
}
.upload-zone video {
  width: 100%;
  max-height: min(420px, 48vh);
  min-width: 0;
  min-height: 0;
  background: #0d1633;
}
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
}
.chip-row button {
  padding: 9px 14px;
  background: #f7f7fb;
  border: 1px solid #e5e5f0;
  border-radius: 999px;
  font-weight: 750;
}
.chip-row button.selected {
  color: #6557d5;
  background: #eeebff;
  border-color: #8a7ee8;
}
.custom-input,
textarea {
  width: 100%;
  margin-top: 10px;
  padding: 13px;
  border: 1px solid #dedfeb;
  border-radius: 13px;
}
.analysis-box {
  margin-top: 20px;
  padding: 17px;
  background: #f3f0ff;
  border: 1px solid #d8d1ff;
  border-radius: 18px;
}

.mission-selection {
  border-color: #dfe2ef;
  border-radius: 12px;
  color: #4b526d;
  font-size: 13px;
}

.mission-selection-help {
  display: block;
  margin-top: 6px;
  color: #9399ae;
  font-size: 11px;
}
.analysis-box div {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.analysis-box span {
  color: #737a90;
  font-size: 0.82rem;
}
.analysis-box button {
  width: 100%;
  padding: 13px;
  color: #fff;
  background: linear-gradient(90deg, #705ef0, #bd36f5);
  border: 0;
  border-radius: 13px;
  font-weight: 850;
}
.analysis-box button.is-analyzing {
  position: relative;
  overflow: visible;
  isolation: isolate;
  min-height: 62px;
  padding: 8px 13px;
  background: #d8edf7;
  color: #fff;
  opacity: 1;
}
.analysis-ocean-fill {
  position: absolute;
  right: auto;
  bottom: 0;
  left: 0;
  z-index: 0;
  width: var(--analysis-progress);
  height: 50%;
  overflow: hidden;
  border-radius: 0 0 0 13px;
  background:
    linear-gradient(180deg, #36d2f2 0%, #16a9df 52%, #0b78be 100%);
  background-size: 100% 100%;
  will-change: width;
  transition: width 180ms linear;
  pointer-events: none;
}
.analysis-ocean-fill::before {
  position: absolute;
  top: -4px;
  left: -8px;
  width: 120%;
  height: 11px;
  content: "";
  background:
    radial-gradient(
      ellipse at 12px 11px,
      rgba(255, 255, 255, 0.72) 0 5px,
      transparent 6px 16px
    ),
    radial-gradient(
      ellipse at 18px 7px,
      rgba(206, 247, 255, 0.7) 0 3px,
      transparent 4px 13px
    );
  background-position: 0 0, 22px 4px;
  background-size: 48px 12px, 62px 10px;
  animation: analysis-ocean-wave 0.75s linear infinite;
  will-change: transform;
}
.analysis-ocean-fill::after {
  position: absolute;
  inset: 0;
  content: "";
  background: linear-gradient(
    110deg,
    transparent 35%,
    rgba(255, 255, 255, 0.24) 50%,
    transparent 65%
  );
  background-size: 220% 100%;
  animation: analysis-ocean-shimmer 1.8s ease-in-out infinite;
  pointer-events: none;
}
.analysis-penguin-loader {
  position: absolute;
  bottom: 2px;
  left: clamp(-60px, calc(var(--analysis-progress) - 60px), calc(100% - 120px));
  z-index: 2;
  width: 120px;
  height: 80px;
  object-fit: contain;
  clip-path: inset(27% 0 7% 33%);
  filter: drop-shadow(0 2px 2px rgba(34, 104, 145, 0.25));
  pointer-events: none;
  will-change: left, transform;
  transform-origin: center bottom;
  animation: analysis-penguin-bob 1.7s ease-in-out infinite;
  transition: left 180ms linear;
}
.analysis-surf-wave-loader {
  position: absolute;
  right: auto;
  bottom: 2px;
  left: clamp(-60px, calc(var(--analysis-progress) - 60px), calc(100% - 120px));
  z-index: 1;
  width: 120px;
  height: 80px;
  pointer-events: none;
  will-change: left, transform;
  animation: analysis-penguin-bob 1.7s ease-in-out infinite;
  transition: left 180ms linear;
}
.analysis-surf-wave-art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  -webkit-mask-image: radial-gradient(
    ellipse 32% 39% at 69% 61%,
    transparent 0 96%,
    #000 100%
  );
  mask-image: radial-gradient(
    ellipse 32% 39% at 69% 61%,
    transparent 0 96%,
    #000 100%
  );
}
.analysis-surf-wave-ripple {
  position: absolute;
  z-index: 1;
  height: 9px;
  border-top: 3px solid rgba(255, 255, 255, 0.86);
  border-radius: 50%;
  opacity: 0.8;
  pointer-events: none;
  animation: analysis-surf-ripple 0.95s ease-in-out infinite;
}
.analysis-surf-wave-ripple.ripple-one {
  bottom: 9px;
  left: 29px;
  width: 70px;
}
.analysis-surf-wave-ripple.ripple-two {
  right: 3px;
  bottom: 3px;
  width: 48px;
  border-top-width: 2px;
  opacity: 0.62;
  animation-delay: -0.34s;
  animation-duration: 0.78s;
}
.analysis-surf-wave-ripple.ripple-three {
  bottom: 18px;
  left: 12px;
  width: 34px;
  border-top-width: 2px;
  opacity: 0.56;
  animation-delay: -0.58s;
  animation-duration: 1.18s;
}
.analysis-button-label {
  position: relative;
  z-index: 3;
  color: #fff !important;
  font-size: inherit;
  font-weight: inherit;
  opacity: 1 !important;
  text-shadow: 0 1px 2px rgba(44, 27, 105, 0.18);
}
.analysis-box button.is-analyzing:disabled {
  color: #fff;
  opacity: 1 !important;
}
@keyframes analysis-ocean-wave {
  from {
    transform: translateX(-24px);
  }
  to {
    transform: translateX(0);
  }
}
@keyframes analysis-ocean-shimmer {
  from {
    background-position: -20% 0;
  }
  to {
    background-position: 120% 0;
  }
}
@keyframes analysis-surf-ripple {
  0%,
  100% {
    transform: translateX(-5px) scaleX(0.88);
  }
  50% {
    transform: translateX(7px) scaleX(1.08);
  }
}
@keyframes analysis-penguin-bob {
  0%,
  100% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(1px, -3px);
  }
}
.analysis-progress-label {
  margin-top: 7px;
  color: #7565d8;
  font-size: 0.76rem;
  font-weight: 750;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
@media (prefers-reduced-motion: reduce) {
  .analysis-ocean-fill,
  .analysis-penguin-loader,
  .analysis-surf-wave-loader {
    transition: none;
  }
  .analysis-ocean-fill::before,
  .analysis-ocean-fill::after {
    animation: none;
  }
  .analysis-penguin-loader {
    animation: none;
  }
  .analysis-surf-wave-loader {
    animation: none;
  }
  .analysis-surf-wave-ripple {
    animation: none;
  }
}
.result-box {
  margin-top: 12px;
  padding: 15px;
  background: #f6f6fa;
  border-radius: 16px;
}
.result-box > span {
  font-weight: 800;
}
.result-box > small {
  margin-left: 8px;
  color: #969caf;
}
.result-box > div {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.result-box input {
  width: 100%;
  padding: 12px;
  border: 1px solid #dedfeb;
  border-radius: 12px;
}
.result-box > div.saving-feedback-section {
  display: grid;
  align-items: stretch;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #dfe8e3;
}
.saving-feedback-section > strong {
  font-size: 0.9rem;
}
.saving-feedback-buttons {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  width: 100%;
}
.saving-feedback-buttons button {
  min-width: 0;
  min-height: 40px;
  padding: 9px 6px;
  color: #61677a;
  background: #fff;
  border: 1px solid #d9dce8;
  border-radius: 11px;
  font-size: 0.86rem;
  font-weight: 750;
  white-space: nowrap;
}
.saving-feedback-buttons button.selected {
  color: #287b5b;
  background: #e4faef;
  border-color: #54bd8d;
}
.verified-amount-row {
  display: grid;
  gap: 7px;
  width: 100%;
}
.verified-amount-row label {
  color: #626a7e;
  font-size: 0.82rem;
  font-weight: 750;
}
.verified-amount-row > div {
  display: flex;
  align-items: center;
  gap: 8px;
}
.verified-amount-row input {
  flex: 1;
}
.feedback-help {
  color: #6e768a;
  line-height: 1.45;
}
.result-box.ready {
  background: #f0fff7;
}
.result-box.ready > span {
  color: #328665;
}
textarea {
  min-height: 84px;
  resize: vertical;
}
.share-notice {
  padding: 10px;
  margin: 12px 0 0;
  color: #737a90;
  background: #f6f6fa;
  border-radius: 11px;
  font-size: 0.8rem;
}
.upload-modal > footer {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 12px;
  padding: 16px 28px 24px;
}
.upload-modal > footer button {
  padding: 14px;
  border-radius: 13px;
  font-weight: 850;
}
.cancel {
  color: #687086;
  background: #fff;
  border: 1px solid #dedfeb;
}
.submit {
  color: #fff;
  background: #6d5ddd;
  border: 0;
}
.submit:disabled,
.analysis-box button:disabled,
.saving-feedback-buttons button:disabled {
  opacity: 0.55;
}
@keyframes focus-pulse {
  from {
    box-shadow: 0 0 0 12px #8d80ff35;
  }
  to {
    box-shadow:
      0 0 0 5px #8d80ff2e,
      0 18px 38px #29315a35;
  }
}
@keyframes feed-card-enter {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
@media (max-width: 1200px) {
  .feed-header {
    width: 100%;
  }
  .feed-layout {
    grid-template-columns: 1fr;
  }
  .feed-sidebar {
    position: static;
    width: auto;
    height: auto;
  }
  .chat-room {
    flex: none;
    height: 600px;
    min-height: 600px;
  }
  .floating-add {
    right: 20px;
    bottom: 20px;
  }
  .floating-actions {
    right: 20px;
    bottom: 20px;
  }
}
@media (max-width: 650px) {
  .feed-header {
    width: 100%;
    margin-bottom: 20px;
  }
  .feed-header :deep(.app-page-header__title) {
    font-size: 1.35rem;
  }
  .feed-alert-content {
    align-items: flex-start;
    flex-direction: column;
  }
  .feed-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .feed-toolbar .feed-tabs {
    width: 100%;
  }
  .feed-toolbar .feed-header-actions {
    align-self: flex-end;
    gap: 4px;
  }
  .feed-toolbar .feed-tabs button {
    flex: 1;
  }
  .modal-layer {
    padding: 0;
  }
  .upload-modal {
    height: 100%;
    max-height: none;
    border-radius: 0;
  }
  .feed-caption-row {
    align-items: flex-start;
    flex-direction: column;
  }
  .feed-owner-actions {
    align-self: flex-end;
  }
}

/* Dashboard-style visual treatment for the challenge feed. */
:global(.page-content:has(.feed-page)) {
  background: #f6f8fb;
}

.feed-page {
  padding: 0 22px 22px;
  border: 0;
  border-radius: 30px;
  background: #f6f8fb;
}

.feed-header {
  width: calc(100% - 380px);
  box-sizing: border-box;
  min-height: 0;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  display: block;
}

.feed-header-top,
.feed-header-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.feed-header-top {
  align-items: center;
}

.feed-title-group {
  display: flex;
  min-width: 0;
  flex: 1;
  height: 75px;
  align-items: center;
  gap: 16px;
}

.feed-challenge-name {
  flex: 0 1 auto;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: #1b2d50;
  font-size: clamp(1.65rem, 2.7vw, 2.25rem);
  font-weight: 800;
  letter-spacing: -0.05em;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-shadow: 0 2px 0 rgb(31 56 95 / 12%);
}

.feed-header-bottom {
  margin-top: 0;
  margin-bottom: 22px;
}

.feed-header :deep(.app-page-header__title) {
  color: #1b2d50;
  font-size: clamp(1.55rem, 2.5vw, 2.15rem);
  letter-spacing: -0.05em;
}

.feed-header :deep(.app-page-header__description) {
  color: #7b8eae;
  font-size: 0.82rem;
}

.feed-layout {
  grid-template-columns: minmax(0, 1fr) minmax(350px, 380px);
  gap: 0;
}

.feed-sidebar {
  position: fixed;
  top: calc(var(--wallo-page-top-offset) + 22px);
  right: max(16px, calc((100vw - 1453px) / 2));
  width: 380px;
  height: calc(100vh - var(--wallo-page-top-offset) - 46px);
}

.feed-toolbar {
  padding: 9px;
  margin-bottom: 16px;
  border: 1px solid #dfe8f7;
  border-radius: 17px;
  background: rgb(255 255 255 / 88%);
  box-shadow: 0 8px 20px rgb(88 117 170 / 8%);
}

.feed-tabs {
  background: #eef2fb;
  box-shadow: 0 4px 10px rgb(92 122 194 / 12%);
}

.feed-tabs button {
  color: #8292ae;
}

.feed-tabs button.active {
  background: linear-gradient(135deg, #668cf0, #8c78e7);
  box-shadow: none;
}

.feed-invite-panel button {
  color: #4775c4;
  background: #f0f5ff;
  border-color: #cfdef8;
  box-shadow: 0 4px 10px rgb(92 122 194 / 16%);
}

.feed-header-actions,
.feed-invite-panel {
  width: auto;
}

.feed-invite-panel button {
  width: auto;
}

.feed-leave-button.app-button {
  color: #e26b7d;
}

.saving-total {
  display: flex;
  position: relative;
  top: 22px;
  height: auto;
  min-height: 0;
  box-sizing: border-box;
  width: max-content;
  min-width: max-content;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  flex-shrink: 0;
  padding: 0;
  text-align: left;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  white-space: nowrap;
}

.saving-total small {
  color: #5f7fdb;
  font-size: 0.65rem;
}

.saving-total strong {
  color: #5f7fdb;
  font-size: 1.525rem;
  letter-spacing: -0.04em;
  white-space: nowrap;
}

.empty-feed {
  border-color: #d7e6f8;
  background: rgb(255 255 255 / 85%);
  box-shadow: 0 12px 28px rgb(88 117 170 / 8%);
}

.feed-card {
  border: 1px solid #dce6f3;
  border-radius: 23px;
  background: #fff;
  box-shadow: 0 14px 28px rgb(75 100 143 / 12%);
}

.feed-card header {
  padding: 17px 20px;
  color: #273b61;
  border-bottom: 1px solid #eef4fa;
}

.feed-card header img {
  box-shadow: 0 0 0 4px #f1f5ff;
}

.feed-card header small {
  color: #7d8ead;
}

.saving-badge {
  color: #456fca;
  background: #edf3ff;
}

.feed-media-wrap,
.feed-media,
.feed-media-wrap > video {
  background: #eef5ff;
}

.feed-like-row {
  bottom: 15px;
}

.like-button {
  color: #e46682;
}

.like-button span,
.like-button strong {
  color: #e46682;
}

.like-button .like-icon {
  display: inline-grid;
  width: 1.25rem;
  height: 1.25rem;
  place-items: center;
  font-family: "bootstrap-icons" !important;
  font-size: 1.15rem;
  line-height: 1;
}

.like-button .like-icon i,
.like-burst i {
  font-family: "bootstrap-icons" !important;
  font-style: normal;
  line-height: 1;
}

.mention-feed-button {
  color: #fff;
}

.feed-card footer {
  color: #273b61;
  background: #fff;
}

.feed-card footer span {
  color: #8292ae;
}

.chat-room {
  color: #273b61;
  border: 1px solid #dce7f7;
  border-radius: 22px;
  background: #f6f8fb;
  box-shadow: 0 14px 28px rgb(75 100 143 / 10%);
}

.chat-room > header {
  padding: 19px;
  border-bottom-color: #dfe8f7;
  background: #fff;
  justify-content: space-between;
}

.chat-room-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.chat-room .feed-leave-button.app-button {
  width: auto;
  height: 36px;
  padding: 0 4px;
  color: #f04f5f;
  border-radius: 8px;
  font-size: 0.9rem;
}

.chat-room .feed-leave-button.app-button :deep(.app-button__label) {
  display: inline;
}

.chat-room h2 {
  color: #273b61;
}

.chat-room header small {
  color: #7d8ead;
}

.messages {
  background: #f6f8fb;
  scrollbar-color: #c5d5ee transparent;
}

.message-author {
  color: #3d91a2;
}

.message.mine .message-author {
  color: #b48642;
}

.message-bubble {
  color: #435878;
  background: #f0f5fc;
}

.message.mine .message-bubble {
  color: #fff;
  background: #718fe8;
}

.chat-form {
  align-items: center;
  gap: 10px;
  padding: 16px 14px 18px;
  background: #fff;
  border-radius: 0 0 22px 22px;
}

.chat-form textarea {
  min-height: 56px;
  padding: 15px 18px;
  color: #30486e;
  background: #fff;
  border: 1px solid #dce6f5;
  border-radius: 24px;
  font-size: 1rem;
}

.chat-form button,
.floating-add {
  background: linear-gradient(135deg, #668cf0, #8c78e7);
  box-shadow: 0 10px 22px rgb(102 140 240 / 24%);
}

.chat-form button {
  width: 56px;
  height: 56px;
  flex: 0 0 56px;
  font-size: 1.3rem;
}

@media (max-width: 1200px) {
  .feed-page {
    padding: 16px;
  }

  .feed-header {
    width: 100%;
  }

  .feed-sidebar {
    position: static;
    width: auto;
    height: auto;
  }
}

@media (max-width: 650px) {
  .feed-page {
    padding: 0 10px 10px;
    border-radius: 20px;
  }

  .feed-header {
    padding: 18px;
    border-radius: 18px;
  }

  .feed-header-top,
  .feed-header-bottom {
    align-items: stretch;
    flex-direction: column;
  }

  .feed-title-group {
    width: 100%;
    height: auto;
    min-height: 0;
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .feed-header .saving-total {
    width: max-content;
    top: 0;
  }

  .feed-header-bottom {
    margin-top: 14px;
    margin-bottom: 0;
  }
}
</style>
