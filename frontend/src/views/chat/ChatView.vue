<script setup>
import { computed, nextTick, onMounted, ref, watch } from "vue"
import { storeToRefs } from "pinia"
import { useRoute, useRouter } from "vue-router"

import ChatInput from "@/components/chat/ChatInput.vue"
import ChatMessage from "@/components/chat/ChatMessage.vue"
import AppDialog from "@/components/common/AppDialog.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppState from "@/components/ui/AppState.vue"
import GoalInterviewCard from "@/components/chat/GoalInterviewCard.vue"
import GoalAccountSelector from "@/components/goal/GoalAccountSelector.vue"
import { useConversationStore } from "@/stores/conversationStore"
import { useGoalStore } from "@/stores/goalStore"
import { useUserStore } from "@/stores/userStore"

const WELCOME_MESSAGE = {
  id: "welcome",
  role: "assistant",
  content: "안녕하세요. 저는 Wallo 금융 컨설턴트입니다. 무엇을 도와드릴까요?",
}
const GOAL_SETTING_START_QUERY = "goal-setting"
const GOAL_SETTING_TITLE = "목표 설정"
const GOAL_CHAT_DELETE_BLOCK_MESSAGE =
  "목표 설정이 완료된 채팅은 계좌 변경에 필요하므로 삭제할 수 없습니다."
const GOAL_COMPLETION_DIALOG_MESSAGE =
  "목표 설정 및 로드맵이 완성되었습니다!\nAI 컨설팅 페이지에서 나의 목표와 로드맵을 확인해보세요."
const TIMING_LOG_PREFIX = "[WALLO_TIMING]"

const timingNow = () => (typeof performance !== "undefined" ? performance.now() : Date.now())

const createRequestId = () => {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `wallo-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const logTiming = (event, details = {}) => {
  console.info(`${TIMING_LOG_PREFIX} ${event}`, details)
}

const conversationStore = useConversationStore()
const goalStore = useGoalStore()
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const {
  conversations,
  activeConversation,
  activeConversationId,
  messages,
  activeGoalInterview,
  confirmedGoal,
  isLoading: isConversationLoading,
  isMessageLoading,
  isSending: isChatLoading,
  lastError: conversationError,
  lastErrorStatus: conversationErrorStatus,
  initialLoading: isConversationInitialLoading,
  refreshing: isConversationRefreshing,
  initialMessageLoading: isMessageInitialLoading,
  refreshingMessages: isMessageRefreshing,
} = storeToRefs(conversationStore)
const { availableAccounts, isAccountLoading, isAccountSaving, accountError } =
  storeToRefs(goalStore)
const { user } = storeToRefs(userStore)

const errorMessage = ref("")
const messageList = ref(null)
const editingConversationId = ref(null)
const editingTitle = ref("")
const deleteTargetConversation = ref(null)
const isDeletingConversation = ref(false)
const isConsumptionAnalysisStarting = ref(false)
const isGoalSettingEntry = ref(false)
const isGoalSettingStarting = ref(false)
const isMissingGoalConversation = ref(false)
const isGoalCompletionChecking = ref(false)
const isGoalCompletionDialogVisible = ref(false)
const isGoalRoadmapReady = ref(false)
const isGoalAccountConfigured = ref(false)
const userId = computed(() => user.value?.id ?? null)
const isGoalDeleteBlocked = computed(() => deleteTargetConversation.value?.hasGoal === true)
const deleteDialogTitle = computed(() =>
  isGoalDeleteBlocked.value ? "삭제할 수 없는 채팅" : "채팅 삭제",
)
const deleteDialogMessage = computed(() =>
  isGoalDeleteBlocked.value
    ? GOAL_CHAT_DELETE_BLOCK_MESSAGE
    : `'${deleteTargetConversation.value?.title ?? ""}' 채팅방을 삭제할까요?`,
)
const deleteDialogConfirmText = computed(() => (isGoalDeleteBlocked.value ? "확인" : "삭제"))
const displayMessages = computed(() => {
  const consumptionAnalysisStarting = isConsumptionAnalysisStarting.value
  const goalSettingStarting = isGoalSettingStarting.value

  if (isMessageInitialLoading.value && !messages.value.length) return []
  if (consumptionAnalysisStarting || goalSettingStarting) {
    return []
  }
  if (
    route.query.action === "consumption-analysis" ||
    route.query.start === GOAL_SETTING_START_QUERY
  ) {
    return []
  }
  if (isMissingGoalConversation.value) return []
  if (messages.value.length) return messages.value
  if (isGoalSettingEntry.value) return []
  return [{ ...WELCOME_MESSAGE }]
})

const resetGoalCompletionFlow = () => {
  isGoalCompletionDialogVisible.value = false
  isGoalRoadmapReady.value = false
  isGoalAccountConfigured.value = false
}

const showGoalCompletionDialogIfReady = () => {
  if (!isGoalRoadmapReady.value || !isGoalAccountConfigured.value) {
    return
  }

  isGoalRoadmapReady.value = false
  isGoalAccountConfigured.value = false
  isGoalCompletionDialogVisible.value = true
}

watch(
  () => confirmedGoal.value?.goalId,
  async (goalId) => {
    if (!goalId) {
      goalStore.invalidateAvailableAccounts()
      return
    }

    await goalStore.fetchAvailableAccounts({
      notifyError: false,
    })
  },
  { immediate: true },
)

const formatUpdatedAt = (updatedAt) => {
  if (!updatedAt) return ""

  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
  }).format(new Date(updatedAt))
}

const selectConversation = async (conversationId) => {
  if (!userId.value) return

  isGoalSettingEntry.value = false
  isGoalSettingStarting.value = false
  isMissingGoalConversation.value = false
  resetGoalCompletionFlow()
  errorMessage.value = ""
  await conversationStore.selectConversation(conversationId, userId.value)
  await scrollToBottom()
}

const startNewConversation = async () => {
  if (!userId.value) {
    errorMessage.value = "로그인 사용자 정보를 확인할 수 없습니다."
    return
  }

  isGoalSettingEntry.value = false
  isGoalSettingStarting.value = false
  isMissingGoalConversation.value = false
  resetGoalCompletionFlow()
  const conversation = await conversationStore.startNewConversation(userId.value)

  if (conversation) {
    errorMessage.value = ""
  } else if (conversationError.value) {
    errorMessage.value = conversationError.value
  }
}

async function scrollToBottom(behavior = "smooth") {
  await nextTick()
  messageList.value?.scrollTo?.({
    top: messageList.value.scrollHeight,
    behavior,
  })
}

const startEditingTitle = (conversation) => {
  editingConversationId.value = conversation.conversationId
  editingTitle.value = conversation.title
}

const cancelEditingTitle = () => {
  editingConversationId.value = null
  editingTitle.value = ""
}

const saveConversationTitle = async (conversationId) => {
  const title = editingTitle.value.trim()
  if (!userId.value || !title) return

  const updated = await conversationStore.renameConversation(conversationId, userId.value, title)
  if (updated) cancelEditingTitle()
}

const openDeleteDialog = async (conversation) => {
  if (isDeletingConversation.value) return

  const goals = await goalStore.fetchGoals({
    userId: userId.value,
    notifyError: false,
    force: true,
    syncAccounts: false,
  })
  const hasGoal =
    Boolean(goalStore.error) ||
    (Array.isArray(goals) &&
      goals.some((goal) => Number(goal.conversationId) === Number(conversation.conversationId)))

  deleteTargetConversation.value = {
    ...conversation,
    hasGoal,
  }
}

const closeDeleteDialog = () => {
  if (isDeletingConversation.value) return
  deleteTargetConversation.value = null
}

const confirmDeleteConversation = async () => {
  const conversation = deleteTargetConversation.value
  if (!userId.value || !conversation || isDeletingConversation.value) return

  if (editingConversationId.value === conversation.conversationId) {
    cancelEditingTitle()
  }

  isDeletingConversation.value = true
  try {
    const deleted = await conversationStore.removeConversation(
      conversation.conversationId,
      userId.value,
    )
    if (deleted) {
      deleteTargetConversation.value = null
    }
  } finally {
    isDeletingConversation.value = false
  }
}

const handleDeleteDialogConfirm = () => {
  if (isGoalDeleteBlocked.value) {
    closeDeleteDialog()
    return
  }

  void confirmDeleteConversation()
}

const followTypingMessage = () => scrollToBottom("auto")
const completeTypingMessage = (messageId) => {
  conversationStore.completeMessageAnimation(messageId)
}

async function sendMessage(message, requestId = null) {
  if (isChatLoading.value || isGoalSettingStarting.value || !userId.value) return

  isMissingGoalConversation.value = false
  errorMessage.value = ""
  const sendPromise = conversationStore.sendMessage(
    userId.value,
    message,
    null,
    requestId,
  )
  await scrollToBottom()
  const sent = await sendPromise
  await scrollToBottom()
  if (!sent && conversationError.value) {
    errorMessage.value = conversationError.value
  }
  return sent
}

const handleAccountSelect = async (accountId) => {
  const goalId = confirmedGoal.value?.goalId
  if (!goalId) return

  const startedAt = timingNow()
  logTiming("goal.account.start", { goalId })
  try {
    const selectedAccount = await goalStore.saveGoalAccount(goalId, accountId)
    if (!selectedAccount) {
      logTiming("goal.account.end", {
        goalId,
        status: "not_saved",
        elapsedMs: Math.round(timingNow() - startedAt),
      })
      return
    }

    await conversationStore.fetchConfirmedGoal(activeConversationId.value, {
      force: true,
    })
    await goalStore.fetchAvailableAccounts({ notifyError: false })
    isGoalAccountConfigured.value = true
    showGoalCompletionDialogIfReady()
    logTiming("goal.account.end", {
      goalId,
      status: "completed",
      elapsedMs: Math.round(timingNow() - startedAt),
    })
  } catch {
    logTiming("goal.account.end", {
      goalId,
      status: "failed",
      elapsedMs: Math.round(timingNow() - startedAt),
    })
    // goalStore가 API 오류와 사용자 알림을 처리한다.
  }
}

const confirmGoal = async () => {
  const startedAt = timingNow()
  const requestId = createRequestId()
  logTiming("goal.confirm.start", { requestId })
  resetGoalCompletionFlow()
  const sent = await sendMessage("이대로 확정할게", requestId)
  if (!sent || activeGoalInterview.value?.action !== "CONFIRM") {
    logTiming("goal.confirm.end", {
      requestId,
      status: sent ? "not_confirmed" : "message_failed",
      elapsedMs: Math.round(timingNow() - startedAt),
    })
    return
  }

  const goalId = confirmedGoal.value?.goalId
  if (!goalId) {
    errorMessage.value = "목표 설정 결과를 확인하지 못했습니다."
    logTiming("goal.confirm.end", {
      requestId,
      status: "goal_not_found",
      elapsedMs: Math.round(timingNow() - startedAt),
    })
    return
  }

  isGoalCompletionChecking.value = true
  try {
    const roadmap = await goalStore.fetchGoalRoadmap(goalId, {
      notifyError: false,
      force: true,
    })
    if (roadmap?.generationStatus === "COMPLETED") {
      isGoalRoadmapReady.value = true
      showGoalCompletionDialogIfReady()
      logTiming("goal.confirm.end", {
        requestId,
        goalId,
        status: "roadmap_ready",
        elapsedMs: Math.round(timingNow() - startedAt),
      })
      return
    }

    isGoalRoadmapReady.value = false
    errorMessage.value =
      roadmap?.generationStatus === "FAILED"
        ? "목표 설정은 완료되었지만 로드맵 생성에 실패했습니다."
        : "목표 설정 결과를 확인하지 못했습니다."
    logTiming("goal.confirm.end", {
      requestId,
      goalId,
      status: roadmap?.generationStatus === "FAILED" ? "roadmap_failed" : "roadmap_not_ready",
      elapsedMs: Math.round(timingNow() - startedAt),
    })
  } finally {
    isGoalCompletionChecking.value = false
  }
}

const cancelGoal = async () => {
  resetGoalCompletionFlow()
  await sendMessage("그만할래")
}

const closeGoalCompletionDialog = () => {
  isGoalCompletionDialogVisible.value = false
}

const openAiConsulting = async () => {
  isGoalCompletionDialogVisible.value = false
  try {
    await router.push({ name: "ai-consulting" })
  } catch (error) {
    errorMessage.value = error.message || "AI 컨설팅 페이지로 이동하지 못했습니다."
  }
}

const startGoalSettingConversation = async () => {
  if (isGoalSettingStarting.value || !userId.value) return

  isGoalSettingEntry.value = true
  isGoalSettingStarting.value = true
  isMissingGoalConversation.value = false
  resetGoalCompletionFlow()
  errorMessage.value = ""

  try {
    await router.replace({ name: "chat" })

    const started = await conversationStore.startGoalSettingConversation(userId.value)
    if (!started) {
      errorMessage.value = conversationErrorStatus.value === 429
        ? conversationError.value
        : "목표 설정 채팅을 시작하지 못했습니다."
    }
  } catch (error) {
    errorMessage.value = error.message || "목표 설정 채팅을 시작하지 못했습니다."
  } finally {
    isGoalSettingStarting.value = false
    await scrollToBottom()
  }
}

const startConsumptionAnalysis = async () => {
  if (isConsumptionAnalysisStarting.value || !userId.value) return

  isConsumptionAnalysisStarting.value = true
  isMissingGoalConversation.value = false
  resetGoalCompletionFlow()
  errorMessage.value = ""
  await router.replace({ name: "chat" })

  try {
    const started = await conversationStore.startConsumptionAnalysis(userId.value)
    if (!started) {
      errorMessage.value = conversationErrorStatus.value === 429
        ? conversationError.value
        : "소비분석 채팅을 시작하지 못했습니다."
    }
  } finally {
    isConsumptionAnalysisStarting.value = false
    await scrollToBottom()
  }
}

watch(
  () => route.query.action,
  (action) => {
    if (action === "consumption-analysis") {
      void startConsumptionAnalysis()
    }
  },
)

watch(
  () => route.query.start,
  (start) => {
    if (start === GOAL_SETTING_START_QUERY) {
      void startGoalSettingConversation()
    }
  },
)

onMounted(async () => {
  if (!userId.value) {
    await userStore.restoreSession()
  }

  if (!userId.value) {
    errorMessage.value = "로그인 사용자 정보를 확인할 수 없습니다."
    return
  }

  if (route.query.action === "consumption-analysis") {
    await startConsumptionAnalysis()
    return
  }

  if (route.query.start === GOAL_SETTING_START_QUERY) {
    await startGoalSettingConversation()
    return
  }

  const hasConversationQuery = route.query.conversationId !== undefined
  const fallbackConversationId = await conversationStore.fetchConversations(userId.value, {
    force: hasConversationQuery,
  })
  const requestedConversationId = Number(route.query.conversationId)
  const hasRequestedConversation =
    Number.isInteger(requestedConversationId) &&
    requestedConversationId > 0 &&
    conversationStore.conversations.some(
      (conversation) => Number(conversation.conversationId) === requestedConversationId,
    )

  if (hasConversationQuery && !hasRequestedConversation) {
    activeConversationId.value = null
    await conversationStore.fetchMessages(userId.value, null)
    isMissingGoalConversation.value = true
    errorMessage.value = "목표 설정 채팅을 찾을 수 없습니다."
    return
  }

  const conversationId = hasRequestedConversation ? requestedConversationId : fallbackConversationId

  if (conversationId) {
    if (conversationStore.activeConversationId === conversationId) {
      await conversationStore.fetchMessages(userId.value, conversationId)
    } else {
      await conversationStore.selectConversation(conversationId, userId.value)
    }
    await scrollToBottom()
  }
})
</script>

<template>
  <main class="chat-page">
    <div class="chat-layout">
      <section class="chat-main-column" aria-labelledby="chat-title">
        <AppCard as="section" class="chat-panel" padding="none">
          <header class="chat-panel-header">
            <h1 id="chat-title" class="mb-1 fs-5 fw-bold">
              {{ isGoalSettingEntry ? GOAL_SETTING_TITLE : activeConversation?.title || "새 채팅" }}
            </h1>
            <p class="mb-0 small text-secondary">Wallo AI 금융 컨설턴트</p>
          </header>

          <div ref="messageList" class="message-list" aria-live="polite">
            <AppState
              v-if="isMessageInitialLoading"
              class="message-state"
              type="loading"
              title="대화 내용을 불러오는 중입니다."
              message="잠시만 기다려 주세요."
              compact
              aria-label="대화 내용 불러오는 중"
            />

            <div v-else-if="isMessageRefreshing" class="message-refresh-status" role="status">
              최신 대화를 확인하는 중...
            </div>

            <ChatMessage
              v-for="message in displayMessages"
              :key="message.id"
              :message="message"
              @typing="followTypingMessage"
              @typing-complete="completeTypingMessage"
            />

            <GoalInterviewCard
              v-if="activeGoalInterview"
              :interview="activeGoalInterview"
              :loading="isChatLoading || isGoalCompletionChecking"
              @confirm="confirmGoal"
              @cancel="cancelGoal"
            />

            <GoalAccountSelector
              v-if="confirmedGoal"
              :accounts="availableAccounts"
              :loading="isAccountLoading"
              :saving="isAccountSaving"
              :error="accountError"
              @retry="goalStore.fetchAvailableAccounts()"
              @select-account="handleAccountSelect"
            />

            <div
              v-if="(isConsumptionAnalysisStarting || isGoalSettingStarting) && !isChatLoading"
              class="loading-message"
              aria-label="자동 채팅 준비 중"
            >
              {{
                isGoalSettingStarting
                  ? "목표 설정 채팅을 준비하는 중..."
                  : "소비분석 채팅을 준비하는 중..."
              }}
            </div>

            <div v-if="isChatLoading" class="loading-message" aria-label="AI 답변 생성 중">
              AI 답변을 기다리는 중...
            </div>

            <div
              v-if="isGoalCompletionChecking"
              class="loading-message"
              aria-label="목표 설정 결과 확인 중"
            >
              목표 설정 결과를 확인하는 중...
            </div>
          </div>

          <AppAlert v-if="errorMessage" class="chat-error" variant="danger">
            {{ errorMessage }}
            <RouterLink
              v-if="isMissingGoalConversation"
              to="/dashboard"
              class="chat-dashboard-link"
            >
              대시보드로 이동
            </RouterLink>
          </AppAlert>

          <ChatInput
            :disabled="
              isConsumptionAnalysisStarting ||
              isGoalSettingStarting ||
              isMissingGoalConversation ||
              isGoalCompletionChecking ||
              isChatLoading ||
              isMessageLoading ||
              !userId
            "
            @send="sendMessage"
          />
        </AppCard>
      </section>

      <aside class="chat-sidebar">
        <AppCard as="section" class="conversation-panel" padding="none">
          <div class="conversation-panel-content">
            <AppButton
              class="new-conversation-button"
              block
              :disabled="isConversationLoading || !userId"
              @click="startNewConversation"
            >
              <template #leading>
                <i class="bi bi-plus-lg" aria-hidden="true"></i>
              </template>
              새 채팅
            </AppButton>

            <div class="conversation-panel-heading">
              <h2 class="mb-0 fs-6 fw-bold">채팅 목록</h2>
              <span class="conversation-panel-meta">
                <span v-if="isConversationRefreshing" class="small text-secondary" role="status">
                  갱신 중
                </span>
                <span class="conversation-count">{{ conversations.length }}</span>
              </span>
            </div>

            <AppState
              v-if="isConversationInitialLoading && !conversations.length"
              class="conversation-state"
              type="loading"
              title="채팅 목록을 불러오는 중입니다."
              message="저장된 대화를 확인하고 있습니다."
              compact
            />

            <AppState
              v-else-if="!conversations.length"
              class="conversation-state"
              type="empty"
              title="저장된 채팅이 없습니다."
              message="새 채팅을 시작해 금융 상담을 받아보세요."
              compact
            />

            <div v-else class="conversation-list">
              <div
                v-for="conversation in conversations"
                :key="conversation.conversationId"
                class="conversation-item"
                :class="{
                  active: conversation.conversationId === activeConversationId,
                }"
              >
                <form
                  v-if="editingConversationId === conversation.conversationId"
                  class="conversation-title-form"
                  @submit.prevent="saveConversationTitle(conversation.conversationId)"
                >
                  <input
                    v-model="editingTitle"
                    class="conversation-title-input"
                    maxlength="100"
                    aria-label="채팅방 제목"
                  />
                  <button type="submit" class="conversation-icon-button" aria-label="제목 저장">
                    <i class="bi bi-check-lg"></i>
                  </button>
                  <button
                    type="button"
                    class="conversation-icon-button"
                    aria-label="제목 변경 취소"
                    @click="cancelEditingTitle"
                  >
                    <i class="bi bi-x-lg"></i>
                  </button>
                </form>

                <div v-else class="conversation-item-content">
                  <button
                    type="button"
                    class="conversation-select"
                    @click="selectConversation(conversation.conversationId)"
                  >
                    <span class="conversation-title">
                      {{ conversation.title }}
                    </span>
                    <small class="conversation-date">
                      {{ formatUpdatedAt(conversation.updatedAt) }}
                    </small>
                  </button>
                  <button
                    type="button"
                    class="conversation-action"
                    aria-label="채팅방 제목 변경"
                    @click="startEditingTitle(conversation)"
                  >
                    <i class="bi bi-pencil"></i>
                  </button>
                  <button
                    type="button"
                    class="conversation-action conversation-action--danger"
                    aria-label="채팅방 삭제"
                    @click="openDeleteDialog(conversation)"
                  >
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </AppCard>
      </aside>
    </div>

    <AppDialog
      :visible="Boolean(deleteTargetConversation)"
      :title="deleteDialogTitle"
      :message="deleteDialogMessage"
      :confirm-text="deleteDialogConfirmText"
      cancel-text="취소"
      :show-cancel="!isGoalDeleteBlocked"
      @close="closeDeleteDialog"
      @confirm="handleDeleteDialogConfirm"
    />

    <AppDialog
      :visible="isGoalCompletionDialogVisible"
      title="목표 설정 완료"
      :message="GOAL_COMPLETION_DIALOG_MESSAGE"
      confirm-text="확인하기"
      :show-cancel="false"
      @close="closeGoalCompletionDialog"
      @confirm="openAiConsulting"
    />
  </main>
</template>

<style scoped src="@/assets/styles/chat.css"></style>
