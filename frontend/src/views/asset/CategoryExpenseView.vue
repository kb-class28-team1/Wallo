<script setup>
import { computed, ref } from "vue"
import { storeToRefs } from "pinia"
import CategoryBudgetEditor from "@/components/asset/CategoryBudgetEditor.vue"
import ExpenseCategoryBreakdown from "@/components/asset/ExpenseCategoryBreakdown.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"
import { getExpenses } from "@/api/assetApi"
import { getApiErrorMessage } from "@/utils/apiError"
import { getAppToday } from "@/utils/appDate"
import { useAssetSyncStatus } from "@/composables/useAssetSyncStatus"
import { useCategoryBudgetEditor } from "@/composables/useCategoryBudgetEditor"
import { useAssetStore } from "@/stores/assetStore"
import { useBudgetStore } from "@/stores/budgetStore"

const PAGE_SIZE = 20
const assetStore = useAssetStore()
const budgetStore = useBudgetStore()
const { isSyncing, syncError } = storeToRefs(assetStore)
const {
  categorySummary: budgetSummary,
  error: budgetError,
  initialLoading: budgetInitialLoading,
  refreshing: budgetRefreshing,
  isLoading: isBudgetLoading,
  isSaving: isBudgetSaving,
} = storeToRefs(budgetStore)

const createEmptyExpenseData = () => ({
  totalExpense: 0,
  expenseCategoryBreakdown: [],
})

const formatDate = (date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

const normalizeExpenseData = (data) => ({
  totalExpense: Number(data?.totalExpense) || 0,
  expenseCategoryBreakdown: data?.expenseCategoryBreakdown ?? [],
})

const today = getAppToday()
const selectedMonth = ref(new Date(today.getFullYear(), today.getMonth(), 1))
const expenseData = ref(createEmptyExpenseData())
const hasLoadedData = ref(false)
const isLoading = ref(false)
const error = ref("")
let summaryRequestVersion = 0

const monthLabel = computed(() =>
  new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long" }).format(selectedMonth.value),
)

const targetMonth = computed(() => {
  const year = selectedMonth.value.getFullYear()
  const month = String(selectedMonth.value.getMonth() + 1).padStart(2, "0")
  return `${year}-${month}`
})

const dateRange = computed(() => {
  const year = selectedMonth.value.getFullYear()
  const month = selectedMonth.value.getMonth()
  return {
    startDate: formatDate(new Date(year, month, 1)),
    endDate: formatDate(new Date(year, month + 1, 0)),
  }
})

const isCurrentMonth = computed(() => {
  const today = getAppToday()
  return (
    targetMonth.value === `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`
  )
})

const canEditBudget = computed(
  () => isCurrentMonth.value && !isBudgetLoading.value && !budgetError.value,
)
const isInitialLoading = computed(() => isLoading.value && !hasLoadedData.value)
const isRefreshing = computed(() => isLoading.value && hasLoadedData.value)
const isBudgetInitialLoading = computed(() => budgetInitialLoading?.value ?? isBudgetLoading.value)
const isBudgetRefreshing = computed(() => budgetRefreshing?.value ?? false)
const isPageRefreshing = computed(
  () => isSyncing.value || isRefreshing.value || isBudgetRefreshing.value,
)
const refreshStatusMessage = computed(() => {
  if (isSyncing.value) {
    return "거래내역을 최신 상태로 동기화하고 있습니다."
  }

  if (isRefreshing.value && isBudgetRefreshing.value) {
    return `${monthLabel.value} 소비·예산을 최신 상태로 갱신하고 있습니다.`
  }

  if (isRefreshing.value) {
    return `${monthLabel.value} 소비 내역을 최신 상태로 갱신하고 있습니다.`
  }

  return "카테고리별 예산을 최신 상태로 갱신하고 있습니다."
})
const displayedBudgetError = computed(() => (budgetSummary.value ? "" : budgetError.value))
const isBudgetRefreshError = computed(() => Boolean(budgetSummary.value && budgetError.value))

const loadSummary = async () => {
  const currentRequest = ++summaryRequestVersion
  const isInitialLoad = !hasLoadedData.value
  isLoading.value = true
  error.value = ""

  try {
    const response = await getExpenses({
      ...dateRange.value,
      page: 0,
      size: PAGE_SIZE,
    })

    if (currentRequest !== summaryRequestVersion) return
    if (!response?.success || !response?.data) {
      throw new Error(response?.error?.message || "카테고리별 소비 응답이 올바르지 않습니다.")
    }

    expenseData.value = normalizeExpenseData(response.data)
    hasLoadedData.value = true
  } catch (caughtError) {
    if (currentRequest !== summaryRequestVersion) return

    const message = getApiErrorMessage(
      caughtError,
      "카테고리별 소비를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
    )
    error.value = message
    if (isInitialLoad) {
      expenseData.value = createEmptyExpenseData()
      hasLoadedData.value = false
    }
    alert(message)
  } finally {
    if (currentRequest === summaryRequestVersion) {
      isLoading.value = false
    }
  }
}

const loadSelectedMonth = async ({ forceBudget = false } = {}) => {
  const budgetRequestOptions = forceBudget
    ? { notifyError: false, force: true }
    : { notifyError: false }

  await Promise.all([
    loadSummary(),
    budgetStore.fetchCategoryBudgets(targetMonth.value, budgetRequestOptions).catch(() => null),
  ])
}

const moveMonth = async (offset) => {
  const year = selectedMonth.value.getFullYear()
  const month = selectedMonth.value.getMonth()
  selectedMonth.value = new Date(year, month + offset, 1)
  await loadSelectedMonth()
}

const { syncStatus, syncCurrentMonth } = useAssetSyncStatus({
  isSyncing,
  syncError,
  syncAssets: () => assetStore.syncAssets(),
  reload: () => loadSelectedMonth({ forceBudget: true }),
  reloadError: error,
  reloadFailureMessage: "동기화는 완료되었지만 현재 월 카테고리별 소비를 다시 불러오지 못했습니다.",
})

const {
  isBudgetEditorVisible,
  openBudgetEditor,
  closeBudgetEditor,
  saveBudget,
} = useCategoryBudgetEditor({
  canEditBudget,
  loadInitialData: loadSelectedMonth,
  saveCategoryBudgets: (request) => budgetStore.saveCategoryBudgets(request),
})
</script>

<template>
  <section class="category-expense-view">
    <AppPageHeader class="page-header" title="카테고리별 소비">
      <template #leading>
        <RouterLink to="/assets" class="back-button pressable" aria-label="자산 관리로 돌아가기">
          <i class="bi bi-chevron-left" aria-hidden="true"></i>
        </RouterLink>
      </template>
      <template #title>
        <span class="category-page-title">
          <span>카테고리별 소비</span>
          <span v-if="isPageRefreshing" class="category-refresh-status" role="status">
            <span class="spinner-border spinner-border-sm text-primary" aria-hidden="true"></span>
            <span class="category-refresh-status__text">{{ refreshStatusMessage }}</span>
          </span>
        </span>
      </template>
      <template #actions>
        <button
          type="button"
          class="category-sync-button btn app-action-link pressable"
          data-testid="category-refresh-button"
          :disabled="
            isSyncing ||
            isBudgetSaving ||
            isInitialLoading ||
            isBudgetInitialLoading ||
            isPageRefreshing
          "
          @click="syncCurrentMonth"
        >
          <i class="bi bi-arrow-clockwise me-1" aria-hidden="true"></i>
          새로고침
        </button>
      </template>
    </AppPageHeader>

    <AppAlert
      v-if="syncStatus"
      class="category-sync-status"
      :variant="syncStatus.type"
      :message="syncStatus.message"
      role="status"
    />

    <AppState
      v-if="isInitialLoading"
      class="page-state"
      type="loading"
      title="카테고리별 소비를 불러오는 중입니다."
      message="잠시만 기다려 주세요."
    />

    <AppAlert v-else-if="error && !hasLoadedData" class="page-state" variant="danger">
      <div>
        <strong class="d-block mb-1">카테고리별 소비를 불러오지 못했습니다.</strong>
        <span>{{ error }}</span>
      </div>
      <button
        type="button"
        class="btn btn-outline-danger btn-sm pressable"
        @click="loadSelectedMonth({ forceBudget: true })"
      >
        다시 시도
      </button>
    </AppAlert>

    <template v-else>
      <AppAlert v-if="error && hasLoadedData" class="mb-3" variant="warning">
        <span>최신 카테고리별 소비를 갱신하지 못했습니다. 기존 내역을 표시하고 있습니다.</span>
        <button
          type="button"
          class="btn btn-outline-warning btn-sm pressable"
          @click="loadSelectedMonth({ forceBudget: true })"
        >
          다시 시도
        </button>
      </AppAlert>

      <AppAlert v-if="isBudgetRefreshError" class="mb-3" variant="warning">
        <span>최신 예산 정보를 갱신하지 못했습니다. 기존 예산을 표시하고 있습니다.</span>
        <button
          type="button"
          class="btn btn-outline-warning btn-sm pressable"
          @click="loadSelectedMonth({ forceBudget: true })"
        >
          다시 시도
        </button>
      </AppAlert>

      <ExpenseCategoryBreakdown
        :breakdown="expenseData.expenseCategoryBreakdown"
        :total-expense="expenseData.totalExpense"
        :budget-summary="budgetSummary"
        :budget-loading="isBudgetInitialLoading"
        :budget-error="displayedBudgetError"
        :can-edit-budget="canEditBudget"
        @edit-budget="openBudgetEditor"
      >
        <template #header>
          <div class="month-navigation d-flex align-items-center gap-2">
            <button type="button" class="month-button pressable" aria-label="이전 달" @click="moveMonth(-1)">
              <i class="bi bi-chevron-left" aria-hidden="true"></i>
            </button>
            <strong>{{ monthLabel }}</strong>
            <button type="button" class="month-button pressable" aria-label="다음 달" @click="moveMonth(1)">
              <i class="bi bi-chevron-right" aria-hidden="true"></i>
            </button>
          </div>
        </template>
      </ExpenseCategoryBreakdown>
    </template>

    <CategoryBudgetEditor
      :visible="isBudgetEditorVisible"
      :budget-summary="budgetSummary"
      :target-month="targetMonth"
      :is-saving="isBudgetSaving"
      @close="closeBudgetEditor"
      @save="saveBudget"
    />
  </section>
</template>

<style scoped>
.category-expense-view {
  width: 100%;
  padding: 0 0 var(--wallo-space-6);
}

.category-sync-status {
  margin-bottom: var(--wallo-space-4);
}

.category-page-title {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: var(--wallo-space-3);
}

.category-refresh-status {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: var(--wallo-space-2);
  overflow: hidden;
  color: var(--wallo-color-text-muted);
  font-size: 0.82rem;
  font-weight: 500;
  line-height: 1.4;
  white-space: nowrap;
}

.category-refresh-status__text {
  overflow: hidden;
  text-overflow: ellipsis;
}

.back-button {
  display: inline-flex;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 12px;
  color: #555b6e;
  background: transparent;
  font-size: 1.1rem;
  text-decoration: none;
}

.back-button:hover,
.back-button:focus {
  color: #4d82d6;
  background: #edf6ff;
}

.month-navigation {
  color: var(--wallo-color-text);
}

.month-button {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 10px;
  color: #555b6e;
  background: transparent;
}

.month-button:hover,
.month-button:focus-visible {
  color: #4d82d6;
  background: #edf6ff;
}

.month-button:focus-visible {
  outline: 2px solid var(--wallo-color-primary);
  outline-offset: 2px;
}

.page-state {
  min-height: 260px;
}

@media (max-width: 575.98px) {
  .category-expense-view {
    padding-bottom: var(--wallo-space-5);
  }
}
</style>
