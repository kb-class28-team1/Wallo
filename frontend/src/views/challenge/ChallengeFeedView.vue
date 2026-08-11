<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"
import { useUserStore } from "@/stores/userStore"
import { formatWon } from "@/commonUtils/formatters"
import AppDialog from "@/components/common/AppDialog.vue"
import { leaveChallenge as leaveChallengeRequest } from "@/api/challengeApi"
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
const likingFeedId = ref(null)
const likeBursts = ref([])
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
  return `${protocol}//${window.location.host}/ws/challenges/${challengeId.value}`
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
    scheduleChatReconnect()
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

const openModal = () => {
  modalOpen.value = true
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
}
const validationMessage = ({ requireCaption = false } = {}) => {
  if (!form.file) return "사진이나 영상을 선택해 주세요."
  if (!form.category) return "세부 카테고리를 선택해 주세요."
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
    form.aiEstimatedSavingAmount = Number(result.estimatedSavingAmount) || 0
    form.savingAmount = form.aiEstimatedSavingAmount
    form.savingAmountFeedback = ""
    form.verifiedSavingAmount = null
    form.analysisSummary = result.summary
    form.confidenceScore = result.confidenceScore
    form.analysisDetails = JSON.stringify(result)
  } catch (error) {
    openDialog({ message: error.message })
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
  if (form.savingAmountFeedback === "DIFFERENT"
      && (form.verifiedSavingAmount === null || form.verifiedSavingAmount === ""
        || Number(form.verifiedSavingAmount) < 0)) {
    return "실제 절약 금액을 입력해 주세요."
  }
  return ""
}
const uploadFeed = async () => {
  const invalid = validationMessage({ requireCaption: true })
  if (invalid) return openDialog({ message: invalid })
  if (!form.analysisSummary) return openDialog({ message: "먼저 AI 분석을 진행해 주세요." })
  const feedbackError = savingAmountFeedbackError()
  if (feedbackError) return openDialog({ message: feedbackError })
  isUploading.value = true
  try {
    const data = makeFormData()
    data.append("caption", form.caption)
    data.append("savingAmount", String(form.savingAmount))
    data.append("aiEstimatedSavingAmount", String(form.aiEstimatedSavingAmount))
    data.append("savingAmountFeedback", form.savingAmountFeedback)
    if (form.verifiedSavingAmount !== null && form.verifiedSavingAmount !== "") {
      data.append("verifiedSavingAmount", String(form.verifiedSavingAmount))
    }
    data.append("analysisSummary", form.analysisSummary)
    data.append("confidenceScore", String(form.confidenceScore))
    data.append("analysisDetails", form.analysisDetails)
    await createFeed(challengeId.value, data)
    closeModal()
    await Promise.all([loadFeeds(), loadMessages({ forceScroll: true })])
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
  if (!mention || !setMentionedFeed(mention[2])) return
  chatInput.value = chatInput.value
    .replace(mention[0], "")
    .replace(/\s{2,}/g, " ")
    .trimStart()
}
const mentionFeed = (message) => {
  setMentionedFeed(message.referenceFeedId)
  chatInput.value = ""
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
        <div class="feed-heading-content">
          <span>MY SAVING FEED</span>
          <div class="feed-title-row">
            <h1>{{ challengeName }}</h1>
          </div>
          <p>함께 남긴 절약 기록을 확인하고 응원해 보세요.</p>
        </div>
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
            <div v-if="inviteCode" class="feed-invite-panel">
              <div>
                <small>친구 초대 코드</small>
                <strong>{{ inviteCode }}</strong>
              </div>
              <button type="button" aria-label="초대 코드 복사" @click="copyInviteCode">
                <i class="bi bi-copy" aria-hidden="true"></i>
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
              <img :src="feed.profileImageUrl || '/images/profiles/default-profile.svg'" alt="" />
              <div>
                <strong>{{ feed.nickname }}</strong
                ><small
                  >{{ spendingLabel(feed.spendingType) }} ·
                  {{ categoryLabel(feed.category, feed.customCategory) }}</small
                >
              </div>
              <span class="saving-badge">+ {{ formatWon(feed.savingAmount) }}</span>
            </header>
            <div class="feed-media-wrap">
              <video
                v-if="feed.mediaType === 'VIDEO'"
                class="feed-media"
                :src="feed.mediaUrl"
                autoplay
                muted
                loop
                playsinline
                preload="metadata"
                :aria-label="feed.caption || '절약 인증 영상'"
              ></video>
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
              <span>🤖 AI 분석 완료 · 절약 금액 {{ formatWon(feed.savingAmount) }}</span>
            </footer>
          </article>
        </main>

        <aside class="feed-sidebar">
          <button
            type="button"
            class="feed-leave-button"
            title="챌린지 탈퇴"
            aria-label="챌린지 탈퇴"
            :disabled="isLeavingChallenge"
            @click="leaveCurrentChallenge"
          >
            <i class="bi bi-box-arrow-right" aria-hidden="true"></i>
          </button>
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
                :class="{ mine: item.userId === userStore.user?.id }"
              >
                <strong>{{ item.nickname }}</strong>
                <button v-if="item.referenceFeedId" class="shared-feed" @click="mentionFeed(item)">
                  <video
                    v-if="item.mediaType === 'VIDEO'"
                    :src="item.mediaUrl"
                    autoplay
                    muted
                    loop
                    playsinline
                    preload="metadata"
                    aria-label="공유 피드 영상"
                  ></video>
                  <img v-else :src="item.thumbnailUrl || item.mediaUrl" alt="공유 피드 썸네일" />
                  <span
                    ><b>피드 #{{ item.referenceFeedId }}</b
                    ><small>눌러서 언급하기</small></span
                  >
                </button>
                <p v-if="item.content">{{ item.content }}</p>
              </div>
            </div>
            <div v-if="mentionedFeed" class="mention-preview">
              <span>피드 #{{ mentionedFeed.id }} 언급 중</span>
              <button @click="mentionedFeed = null">×</button>
            </div>
            <form class="chat-form" @submit.prevent="sendMessage">
              <input
                ref="chatInputElement"
                v-model="chatInput"
                placeholder="메시지 보내기..."
                :disabled="isSendingMessage"
                @input="handleChatInput"
                @keydown.enter.exact.prevent
                @keyup.enter.exact.prevent="sendMessage"
              />
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
          <button class="upload-zone" type="button" @click="chooseFile">
            <template v-if="previewUrl">
              <video
                v-if="isVideoFile"
                :src="previewUrl"
                autoplay
                muted
                loop
                playsinline
                preload="metadata"
                aria-label="업로드할 영상 미리보기"
              ></video>
              <img v-else :src="previewUrl" alt="업로드 미리보기" />
            </template>
            <template v-else
              ><span>🖼️</span><strong>사진 / 동영상 업로드</strong
              ><small>클릭해 인증 사진 또는 영상을 올려주세요</small></template
            >
          </button>

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

          <div class="analysis-box">
            <div>
              <b>🤖 AI 분석</b><span>선택 정보와 미디어를 외부 AI 분석기로 전달합니다.</span>
            </div>
            <button type="button" :disabled="isAnalyzing" @click="requestAnalysis">
              {{ isAnalyzing ? "분석 중..." : "✨ AI에게 분석 맡기기" }}
            </button>
          </div>
          <div class="result-box" :class="{ ready: form.analysisSummary }">
            <span>🤖 AI 추정 금액</span
            ><small>{{ form.analysisSummary || "분석하면 예상 절약 금액을 알려드려요." }}</small>
            <div>
              <input
                :value="form.aiEstimatedSavingAmount"
                type="number"
                min="0"
                readonly
                :disabled="!form.analysisSummary"
              /><b>원</b>
            </div>
            <div v-if="form.analysisSummary" class="saving-feedback-section">
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
  margin-bottom: 24px;
}
.feed-heading-content > span {
  color: #7164de;
  font-size: 0.76rem;
  font-weight: 900;
  letter-spacing: 0.14em;
}
.feed-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.feed-header h1 {
  margin: 8px 0 4px;
  font-size: 2rem;
  font-weight: 900;
}
.feed-header p {
  margin: 0;
  color: #939bad;
}
.feed-leave-button {
  display: inline-flex !important;
  visibility: visible !important;
  position: absolute;
  top: 20px;
  left: -58px;
  z-index: 2;
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
  gap: 6px;
  padding: 5px;
  background: #f0eff7;
  border-radius: 13px;
  width: max-content;
}
.feed-tabs button {
  padding: 10px 22px;
  border: 0;
  border-radius: 10px;
  color: #8b91a3;
  background: transparent;
  font-weight: 800;
}
.feed-tabs button.active {
  color: #fff;
  background: #6f61dc;
}
.feed-invite-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 190px;
  padding: 8px 11px 8px 14px;
  background: #fff;
  border: 1px solid #e3e1f4;
  border-radius: 13px;
  box-shadow: 0 5px 15px #29315a0d;
}
.feed-invite-panel div {
  min-width: 0;
  flex: 1;
}
.feed-invite-panel small,
.feed-invite-panel strong {
  display: block;
}
.feed-invite-panel small {
  margin-bottom: 2px;
  color: #989db1;
  font-size: 0.7rem;
  font-weight: 700;
}
.feed-invite-panel strong {
  color: #6658d4;
  font-size: 0.92rem;
  letter-spacing: 0.1em;
}
.feed-invite-panel button {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  color: #6f61dc;
  background: #f0edff;
  border: 0;
  border-radius: 8px;
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
  color: #ffe36e;
  font-size: 0.9rem;
  line-height: 1;
}
.like-button:disabled {
  cursor: wait;
  opacity: 0.6;
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
  padding: 15px 18px 18px;
  color: #fff;
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
.message > strong {
  display: block;
  margin-bottom: 4px;
  color: #8bdfbc;
  font-size: 0.75rem;
}
.message.mine > strong {
  color: #f6cf75;
}
.message p {
  margin: 5px 0;
  font-size: 0.86rem;
  line-height: 1.45;
}
.shared-feed {
  display: flex;
  width: 100%;
  gap: 10px;
  align-items: center;
  padding: 8px;
  color: #fff;
  text-align: left;
  background: #202b4d;
  border: 1px solid #ffffff12;
  border-radius: 11px;
}
.shared-feed img,
.shared-feed video {
  width: 56px;
  height: 50px;
  object-fit: cover;
  border-radius: 8px;
}
.shared-feed span {
  min-width: 0;
}
.shared-feed b,
.shared-feed small {
  display: block;
}
.shared-feed small {
  margin-top: 3px;
  color: #939dbc;
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
  gap: 7px;
  padding: 12px;
  background: #172140;
}
.chat-form input {
  min-width: 0;
  flex: 1;
  padding: 11px 13px;
  color: #fff;
  background: #222d4d;
  border: 0;
  border-radius: 12px;
  outline: 0;
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
  height: 170px;
  overflow: hidden;
  background: #fafbfe;
  border: 2px dashed #d9dcec;
  border-radius: 18px;
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
  width: 100%;
  height: 100%;
  object-fit: contain;
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
  .feed-layout {
    grid-template-columns: 1fr;
  }
  .feed-sidebar {
    position: static;
    width: auto;
    height: auto;
  }
  .feed-leave-button {
    position: static;
    align-self: flex-end;
    margin-bottom: -8px;
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
    margin-bottom: 20px;
  }
  .feed-title-row {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 8px 14px;
  }
  .feed-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .feed-tabs {
    width: 100%;
  }
  .feed-tabs button {
    flex: 1;
  }
  .feed-invite-panel {
    align-self: flex-end;
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
