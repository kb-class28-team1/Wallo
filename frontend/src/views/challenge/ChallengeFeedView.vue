<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"
import { useUserStore } from "@/stores/userStore"
import { getAccessToken } from "@/api/authToken"
import { refreshAccessToken } from "@/api/authApi"
import { formatWon } from "@/commonUtils/formatters"
import arrowPaperPlaneUrl from "@/assets/arrow_paper_plane.svg"
import AppDialog from "@/components/common/AppDialog.vue"
import AuthenticatedImage from "@/components/common/AuthenticatedImage.vue"
import { leaveChallenge as leaveChallengeRequest } from "@/api/challengeApi"
import { getTodayMissions, verifyMissionWithFeed } from "@/api/missionApi"
import {
  analyzeFeed,
  createFeed,
  deleteFeed,
  getFeeds,
  getRoomMessages,
  addFeedLike,
} from "@/api/feedApi"
import {
  EXPENSE_CATEGORY_META,
  FEED_CATEGORY_CODES,
} from "@/features/financial/financialCategories"

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
const activeTab = ref(String(route.query.scope || "ALL").toUpperCase() === "ME" ? "mine" : "all")
const isLoading = ref(true)
const errorMessage = ref("")
const modalOpen = ref(false)
const isAnalyzing = ref(false)
const isUploading = ref(false)
const todayMissions = ref([])
const isMissionLoading = ref(false)
const likingFeedId = ref(null)
const likeBursts = ref([])
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
let dialogResolver = null

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
const getFeedCardClass = (feed) => (isFocusedFeed(feed) ? "focused-feed" : "")
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

const loadFeeds = async () => {
  const data = await getFeeds(challengeId.value, activeTab.value === "mine")
  feeds.value = data.feeds
  challengeName.value = data.challengeName
  inviteCode.value = data.inviteCode || ""
  mySavingTotal.value = data.mySavingTotal
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
const loadMessages = async ({ forceScroll = false } = {}) => {
  const shouldScroll = forceScroll || isNearMessagesBottom()
  const data = await getRoomMessages(challengeId.value)
  messages.value = data.messages
  challengeName.value = data.challengeName
  if (shouldScroll) await scrollMessagesToBottom()
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
const loadPage = async () => {
  isLoading.value = true
  errorMessage.value = ""
  try {
    await Promise.all([loadFeeds(), loadMessages()])
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    isLoading.value = false
  }
}
const changeTab = async (tab) => {
  activeTab.value = tab
  try {
    await loadFeeds()
  } catch (error) {
    errorMessage.value = error.message
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
    disconnectChatSocket()
    await router.replace({ name: "current-challenge" })
  } catch (error) {
    openDialog({ message: error.message })
  } finally {
    isLeavingChallenge.value = false
  }
}

const openModal = async () => {
  modalOpen.value = true
  isMissionLoading.value = true
  try {
    const response = await getTodayMissions()
    todayMissions.value = response.missions.filter((mission) =>
      ["MEDIA_AI", "HYBRID"].includes(mission.verificationType)
      && !mission.completed
      && mission.status !== "VERIFYING")
  } catch (error) {
    todayMissions.value = []
    await openDialog({ message: error.message })
  } finally {
    isMissionLoading.value = false
  }
}
const closeModal = () => {
  modalOpen.value = false
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
    analysisFailed: false,
    dailyMissionId: "",
  })
  if (fileInput.value) fileInput.value.value = ""
}
const addLike = async (feed) => {
  if (likingFeedId.value !== null) return
  likingFeedId.value = feed.id
  try {
    const result = await addFeedLike(challengeId.value, feed.id)
    feed.likeCount = result.likeCount
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
    await Promise.all([loadFeeds(), loadMessages({ forceScroll: true })])
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
  form.analysisFailed = false
}
const selectCategory = (category) => {
  form.category = category
  form.aiEstimatedSavingAmount = 0
  form.savingAmount = 0
  form.savingAmountFeedback = ""
  form.verifiedSavingAmount = null
  form.analysisSummary = ""
  form.confidenceScore = 0
  form.analysisDetails = ""
  form.analysisFailed = false
}
const validationMessage = ({ requireCaption = false } = {}) => {
  if (!form.file) return "사진이나 영상을 선택해 주세요."
  if (!form.category) return "세부 카테고리를 선택해 주세요."
  if (!canConfirmSavingAmount.value) return "먼저 AI 분석을 진행해 주세요."
  if (!Number.isFinite(form.savingAmount) || form.savingAmount < 0) {
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
const requestAnalysis = async () => {
  const invalid = validationMessage()
  if (invalid) return openDialog({ message: invalid })
  isAnalyzing.value = true
  try {
    const result = await analyzeFeed(challengeId.value, makeFormData())
    form.analysisFailed = false
    form.aiEstimatedSavingAmount = Number(result.estimatedSavingAmount) || 0
    form.savingAmount = form.aiEstimatedSavingAmount
    form.savingAmountFeedback = ""
    form.verifiedSavingAmount = null
    form.analysisSummary = result.summary
    form.confidenceScore = result.confidenceScore
    form.analysisDetails = JSON.stringify(result)
  } catch (error) {
    form.analysisFailed = true
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
    if (form.aiEstimatedAmount !== null) {
      data.append("aiEstimatedAmount", String(form.aiEstimatedAmount))
    }
    const feedbackType =
      form.analysisStatus === "AI_COMPLETED"
        ? form.savingAmount === form.aiEstimatedAmount
          ? "ACCEPTED"
          : "ADJUSTED"
        : "MANUAL"
    data.append("feedbackType", feedbackType)
    data.append(
      "analysisStatus",
      form.analysisStatus === "AI_FAILED" ? "AI_FAILED" : "AI_COMPLETED",
    )
    await createFeed(challengeId.value, data)
    data.append("analysisDetails", form.analysisDetails)
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
    await Promise.all([loadFeeds(), loadMessages({ forceScroll: true })])
    if (verificationResult) {
      const resultMessage = verificationResult.decision === "PASS"
        ? `미션을 달성했습니다! +${verificationResult.rewardedPoint}P`
        : verificationResult.decision === "FAIL"
          ? "미션 달성 근거가 부족해 인증에 실패했습니다."
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
  likeBurstTimers.forEach((timer) => window.clearTimeout(timer))
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <section class="feed-page">
    <div v-if="isLoading" class="page-state">
      <div class="spinner-border text-primary"></div>
      <p>챌린지 피드를 불러오고 있어요.</p>
    </div>
    <div v-else-if="errorMessage" class="page-state">
      <strong>{{ errorMessage }}</strong>
      <button class="btn btn-primary" @click="loadPage">다시 시도</button>
    </div>
    <template v-else>
      <header class="feed-header">
        <div class="feed-header-row">
          <h1>{{ challengeName }}</h1>
        </div>
        <p>함께 남긴 절약 기록을 확인하고 응원해 보세요.</p>
      </header>

      <div class="feed-layout">
        <main class="feed-column">
          <div class="feed-toolbar">
            <nav class="feed-tabs">
              <button :class="{ active: activeTab === 'all' }" @click="changeTab('all')">
                전체 피드
              </button>
              <button :class="{ active: activeTab === 'mine' }" @click="changeTab('mine')">
                내 피드
              </button>
            </nav>
            <div class="feed-header-actions">
              <div v-if="inviteCode" class="feed-invite-panel">
                <button type="button" aria-label="초대 코드 복사" @click="copyInviteCode">
                  <i class="bi bi-copy" aria-hidden="true"></i>
                  초대코드 복사
                </button>
              </div>
              <button
                type="button"
                class="feed-leave-button"
                title="챌린지 나가기"
                aria-label="챌린지 나가기"
                :disabled="isLeavingChallenge"
                @click="leaveCurrentChallenge"
              >
                <i class="bi bi-box-arrow-right" aria-hidden="true"></i>
              </button>
            </div>
          </div>
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
                ><span class="d-none">
                  >{{ spendingLabel(feed.spendingType) }} ·

                >
                </span>
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
                  :class="['bi', isFeedMuted(feed.id) ? 'bi-volume-mute-fill' : 'bi-volume-up-fill']"
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
                  ♥
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
                  <img :src="arrowPaperPlaneUrl" alt="" />
                  언급하기
                </button>
                <button
                  type="button"
                  class="like-button"
                  :disabled="likingFeedId === feed.id"
                  aria-label="좋아요 추가"
                  @click.stop="addLike(feed)"
                >
                  <span aria-hidden="true">♥</span>
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
          <div class="saving-total">
            <small>나의 누적 절약 금액</small><strong>{{ formatWon(mySavingTotal) }}</strong>
          </div>
          <section class="chat-room">
            <header>
              <span class="online-dot"></span>
              <div>
                <h2>{{ roomTitle }}</h2>
                <small>피드와 이야기를 함께 나눠요</small>
              </div>
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
      <button class="floating-add" aria-label="절약 피드 추가" @click="openModal">+</button>
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
            <button type="button" :disabled="isAnalyzing" @click="requestAnalysis">
              {{ isAnalyzing ? "분석 중..." : "✨ AI에게 분석 맡기기" }}
            </button>
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
  min-height: calc(100vh - 130px);
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
.feed-header {
  width: calc(100% - 352px);
  margin-bottom: 24px;
}
.feed-header-row {
  display: block;
}
.feed-header h1 {
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
.feed-header p {
  margin: 8px 0 0;
  color: #939bad;
  font-size: 0.67rem;
}
.feed-leave-button {
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
.feed-leave-button:hover:not(:disabled) {
  color: #f05252;
  background: #fff0ef;
}
.feed-leave-button:disabled {
  cursor: wait;
  opacity: 0.6;
}
.saving-total {
  width: 100%;
  box-sizing: border-box;
  padding: 18px 22px;
  background: #f0edff;
  border-radius: 16px;
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
  font-size: 0.78rem;
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
  background: #09122d;
}
.feed-media,
.feed-media-wrap > video {
  display: block;
  width: 100%;
  max-height: 560px;
  object-fit: contain;
  background: #09122d;
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
  z-index: 2;
  overflow: hidden;
  pointer-events: none;
}
.like-burst {
  position: absolute;
  left: 24px;
  bottom: 28px;
  color: #ff6387;
  font-size: 2rem;
  line-height: 1;
  opacity: 0;
  text-shadow: 0 3px 12px #ff638766;
  animation: like-heart-rise 950ms ease-out forwards;
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
.mention-feed-button img {
  width: 24px;
  height: 24px;
  transform: translateY(2px);
  filter: brightness(0) invert(1);
}
.mention-feed-button:hover {
  color: #dcd7ff;
  background: transparent;
}
@keyframes like-heart-rise {
  0% {
    opacity: 0;
    transform: translate3d(0, 12px, 0) scale(0.45) rotate(-10deg);
  }
  16% {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translate3d(var(--like-drift), -150px, 0) scale(1.25) rotate(12deg);
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
  font-size: 2rem;
  box-shadow: 0 10px 28px #6658cf66;
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
}
@media (max-width: 650px) {
  .feed-header {
    width: 100%;
    margin-bottom: 20px;
  }
  .feed-header h1 {
    font-size: 1.35rem;
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
</style>
