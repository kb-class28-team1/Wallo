<script setup>
import { computed, nextTick, onMounted, ref, watch } from "vue"
import { storeToRefs } from "pinia"
import { useRoute, useRouter } from "vue-router"
import ExpenseCalendar from "@/components/asset/ExpenseCalendar.vue"
import CategoryBudgetEditor from "@/components/asset/CategoryBudgetEditor.vue"
import ExpenseCategoryEditModal from "@/components/asset/ExpenseCategoryEditModal.vue"
import ExpenseTransactionList from "@/components/asset/ExpenseTransactionList.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppDialog from "@/components/common/AppDialog.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"
import { getExpenses, updateExpenseCategory } from "@/api/assetApi"
import { getApiErrorMessage } from "@/utils/apiError"
import { formatWon } from "@/utils/formatters"
import { EXPENSE_CATEGORY_META } from "@/features/financial/financialCategories"
import { useAssetStore } from "@/stores/assetStore"
import { useBudgetStore } from "@/stores/budgetStore"

const PAGE_SIZE = 20
const assetStore = useAssetStore()
const budgetStore = useBudgetStore()
const route = useRoute()
const router = useRouter()
const { isSyncing, syncError } = storeToRefs(assetStore)
const {
  categorySummary: budgetSummary,
  error: budgetError,
  isLoading: isBudgetLoading,
  isSaving: isBudgetSaving,
} = storeToRefs(budgetStore)

const createEmptyExpenseData = () => ({
  totalExpense: 0,
  totalIncome: 0,
  expenseCategoryBreakdown: [],
  dailyBreakdown: [],
  transactions: [],
  pagination: {
    currentPage: 0,
    totalPages: 0,
    totalElements: 0,
    hasNext: false,
  },
})

const now = new Date()
const selectedMonth = ref(new Date(now.getFullYear(), now.getMonth(), 1))
const activeView = ref("calendar")
const selectedListCategory = ref("ALL")
const expenseData = ref(createEmptyExpenseData())
const hasLoadedExpenseData = ref(false)
const isLoading = ref(false)
const isLoadingMore = ref(false)
const error = ref("")
const loadMoreError = ref("")
const isDailyModalVisible = ref(false)
const selectedDate = ref("")
const dailyTransactions = ref([])
const dailyPagination = ref(createEmptyExpenseData().pagination)
const isDailyLoading = ref(false)
const isDailyLoadingMore = ref(false)
const dailyError = ref("")
const dailyLoadMoreError = ref("")
const syncStatus = ref(null)
const isBudgetEditorVisible = ref(false)
const isCategoryEditModalVisible = ref(false)
const selectedTransaction = ref(null)
const isCategorySaving = ref(false)
const isCategoryFilterModalVisible = ref(false)
const isCategoryFilterSaving = ref(false)
let requestVersion = 0
let dailyRequestVersion = 0

const monthLabel = computed(() =>
  new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long" }).format(selectedMonth.value),
)

const dateRange = computed(() => {
  const year = selectedMonth.value.getFullYear()
  const month = selectedMonth.value.getMonth()
  return {
    startDate: formatDate(new Date(year, month, 1)),
    endDate: formatDate(new Date(year, month + 1, 0)),
  }
})

const targetMonth = computed(() => {
  const year = selectedMonth.value.getFullYear()
  const month = String(selectedMonth.value.getMonth() + 1).padStart(2, "0")
  return `${year}-${month}`
})

const isCurrentMonth = computed(() => {
  const today = new Date()
  return (
    targetMonth.value === `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`
  )
})

const listCategoryOptions = computed(() => [
  { value: "ALL", label: "전체" },
  ...Object.entries(EXPENSE_CATEGORY_META).map(([value, meta]) => ({
    value,
    label: meta.label,
  })),
])

const selectedListCategoryLabel = computed(
  () =>
    listCategoryOptions.value.find((option) => option.value === selectedListCategory.value)
      ?.label || "전체",
)

const canEditBudget = computed(
  () => isCurrentMonth.value && !isBudgetLoading.value && !budgetError.value,
)

const isExpenseInitialLoading = computed(() => isLoading.value && !hasLoadedExpenseData.value)
const isExpenseRefreshing = computed(() => isLoading.value && hasLoadedExpenseData.value)

const openBudgetEditor = async () => {
  if (!canEditBudget.value) {
    alert("예산은 현재 월에서만 수정할 수 있습니다.")
    return
  }

  isBudgetEditorVisible.value = true
  if (route.query.budget !== "edit") {
    await router.replace({
      query: {
        ...route.query,
        budget: "edit",
      },
    })
  }
}

const closeBudgetEditor = async () => {
  isBudgetEditorVisible.value = false
  if (route.query.budget === "edit") {
    const query = { ...route.query }
    delete query.budget
    await router.replace({ query })
  }
}

const saveBudget = async (request) => {
  try {
    await budgetStore.saveCategoryBudgets(request)
    await closeBudgetEditor()
  } catch {
    // The store handles the user-facing API error message.
  }
}

const hasAnyData = computed(
  () =>
    Number(expenseData.value.totalExpense) > 0 ||
    Number(expenseData.value.totalIncome) > 0 ||
    expenseData.value.transactions.length > 0,
)


const selectedDateLabel = computed(() => {
  if (!selectedDate.value) return ""
  const [year, month, day] = selectedDate.value.split("-").map(Number)
  return `${year}년 ${month}월 ${day}일`
})

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

const normalizeExpenseData = (data) => ({
  totalExpense: Number(data?.totalExpense) || 0,
  totalIncome: Number(data?.totalIncome) || 0,
  expenseCategoryBreakdown: data?.expenseCategoryBreakdown ?? [],
  dailyBreakdown: data?.dailyBreakdown ?? [],
  transactions: data?.transactions ?? [],
  pagination: {
    currentPage: Number(data?.pagination?.currentPage) || 0,
    totalPages: Number(data?.pagination?.totalPages) || 0,
    totalElements: Number(data?.pagination?.totalElements) || 0,
    hasNext: Boolean(data?.pagination?.hasNext),
  },
})

const fetchExpensePage = async (page, append = false) => {
  const currentRequest = ++requestVersion
  const isInitialLoad = !hasLoadedExpenseData.value
  if (append) {
    isLoadingMore.value = true
    loadMoreError.value = ""
  } else {
    isLoading.value = true
    error.value = ""
  }

  try {
    const response = await getExpenses({
      ...dateRange.value,
      page,
      size: PAGE_SIZE,
      category: selectedListCategory.value,
    })

    if (currentRequest !== requestVersion) return
    if (!response?.success || !response?.data) {
      throw new Error(response?.error?.message || "소비 내역 응답이 올바르지 않습니다.")
    }

    const nextData = normalizeExpenseData(response.data)
    if (append) {
      nextData.transactions = [...expenseData.value.transactions, ...nextData.transactions]
    }
    expenseData.value = nextData
    if (!append) {
      hasLoadedExpenseData.value = true
    }
  } catch (caughtError) {
    if (currentRequest !== requestVersion) return

    const message = getApiErrorMessage(
      caughtError,
      append
        ? "추가 거래 내역을 불러오지 못했습니다. 다시 시도해 주세요."
        : "소비 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
    )

    if (append) {
      loadMoreError.value = message
    } else {
      if (isInitialLoad) {
        expenseData.value = createEmptyExpenseData()
        hasLoadedExpenseData.value = false
      }
      error.value = message
    }
    alert(message)
  } finally {
    if (currentRequest === requestVersion) {
      isLoading.value = false
      isLoadingMore.value = false
    }
  }
}

const changeListCategory = async () => {
  await fetchExpensePage(0)
}

const openCategoryFilter = () => {
  if (!isCategoryFilterSaving.value) {
    isCategoryFilterModalVisible.value = true
  }
}

const closeCategoryFilter = () => {
  if (isCategoryFilterSaving.value) return
  isCategoryFilterModalVisible.value = false
}

const applyCategoryFilter = async ({ category }) => {
  if (isCategoryFilterSaving.value) return

  selectedListCategory.value = category || "ALL"
  isCategoryFilterSaving.value = true
  try {
    await changeListCategory()
    if (!error.value) {
      isCategoryFilterModalVisible.value = false
    }
  } finally {
    isCategoryFilterSaving.value = false
  }
}

const openCategoryEditor = (transaction) => {
  if (!transaction?.transactionId) {
    alert("거래 식별자를 확인할 수 없어 카테고리를 수정할 수 없습니다.")
    return
  }

  selectedTransaction.value = transaction
  isCategoryEditModalVisible.value = true
}

const closeCategoryEditor = () => {
  if (isCategorySaving.value) return
  isCategoryEditModalVisible.value = false
  selectedTransaction.value = null
}

const saveTransactionCategory = async ({ transactionId, category }) => {
  if (isCategorySaving.value) return

  isCategorySaving.value = true
  try {
    const response = await updateExpenseCategory(transactionId, category)
    if (!response?.success) {
      throw new Error(response?.error?.message || "카테고리 수정 응답이 올바르지 않습니다.")
    }

    await fetchExpensePage(0)
    if (!error.value) {
      isCategoryEditModalVisible.value = false
      selectedTransaction.value = null
    }
  } catch (caughtError) {
    alert(
      getApiErrorMessage(
        caughtError,
        "카테고리를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      ),
    )
  } finally {
    isCategorySaving.value = false
  }
}

const loadSelectedMonth = async () => {
  requestVersion += 1
  isLoadingMore.value = false
  loadMoreError.value = ""
  await Promise.all([
    fetchExpensePage(0),
    budgetStore.fetchCategoryBudgets(targetMonth.value, { notifyError: false }).catch(() => null),
  ])
}

const syncCurrentMonth = async () => {
  if (isSyncing.value) return

  syncStatus.value = null
  closeDailyModal()

  try {
    const result = await assetStore.syncAssets()
    if (!result) return

    await loadSelectedMonth()
    if (error.value) {
      syncStatus.value = {
        type: "warning",
        message: "동기화는 완료되었지만 현재 월 거래내역을 다시 불러오지 못했습니다.",
      }
      return
    }

    const failedConnections = Number(result.failedConnections) || 0
    const summary = `신규 ${Number(result.inserted) || 0}건, 수정 ${Number(result.updated) || 0}건`
    syncStatus.value =
      failedConnections > 0
        ? {
            type: "warning",
            message: `동기화가 완료되었습니다. ${summary}, 실패한 연결기관 ${failedConnections}건`,
          }
        : {
            type: "success",
            message: `동기화가 완료되었습니다. ${summary}`,
          }
  } catch {
    syncStatus.value = {
      type: "danger",
      message: syncError.value || "자산 거래내역 동기화에 실패했습니다.",
    }
  }
}

const closeDailyModal = () => {
  dailyRequestVersion += 1
  isDailyModalVisible.value = false
  selectedDate.value = ""
  dailyTransactions.value = []
  dailyPagination.value = createEmptyExpenseData().pagination
  dailyError.value = ""
  dailyLoadMoreError.value = ""
  isDailyLoading.value = false
  isDailyLoadingMore.value = false
}

const fetchDailyExpensePage = async (page, append = false) => {
  const currentRequest = ++dailyRequestVersion
  if (append) {
    isDailyLoadingMore.value = true
    dailyLoadMoreError.value = ""
  } else {
    isDailyLoading.value = true
    dailyError.value = ""
  }

  try {
    const response = await getExpenses({
      startDate: selectedDate.value,
      endDate: selectedDate.value,
      page,
      size: PAGE_SIZE,
    })

    if (currentRequest !== dailyRequestVersion) return
    if (!response?.success || !response?.data) {
      throw new Error(response?.error?.message || "선택한 날짜의 응답이 올바르지 않습니다.")
    }

    const nextData = normalizeExpenseData(response.data)
    const dailyTransactionsPage = nextData.transactions.filter((transaction) =>
      ["EXPENSE", "INCOME", "TRANSFER"].includes(transaction.type),
    )
    dailyTransactions.value = append
      ? [...dailyTransactions.value, ...dailyTransactionsPage]
      : dailyTransactionsPage
    dailyPagination.value = nextData.pagination
  } catch (caughtError) {
    if (currentRequest !== dailyRequestVersion) return
    const message = getApiErrorMessage(
      caughtError,
      append
        ? "추가 거래 내역을 불러오지 못했습니다. 다시 시도해 주세요."
        : "선택한 날짜의 거래 내역을 불러오지 못했습니다.",
    )

    if (append) {
      dailyLoadMoreError.value = message
    } else {
      dailyTransactions.value = []
      dailyPagination.value = createEmptyExpenseData().pagination
      dailyError.value = message
    }
    alert(message)
  } finally {
    if (currentRequest === dailyRequestVersion) {
      isDailyLoading.value = false
      isDailyLoadingMore.value = false
    }
  }
}

const openDailyModal = async (date) => {
  selectedDate.value = date
  dailyTransactions.value = []
  dailyPagination.value = createEmptyExpenseData().pagination
  dailyLoadMoreError.value = ""
  isDailyModalVisible.value = true
  await fetchDailyExpensePage(0)
}

const loadMoreDailyExpenses = async () => {
  if (isDailyLoadingMore.value || !dailyPagination.value.hasNext) return
  await fetchDailyExpensePage(dailyPagination.value.currentPage + 1, true)
}

const moveMonth = async (offset) => {
  closeDailyModal()
  const year = selectedMonth.value.getFullYear()
  const month = selectedMonth.value.getMonth()
  selectedMonth.value = new Date(year, month + offset, 1)
  await loadSelectedMonth()
}

const loadMore = async () => {
  if (isLoadingMore.value || !expenseData.value.pagination.hasNext) return
  await fetchExpensePage(expenseData.value.pagination.currentPage + 1, true)
}

watch(
  () => route.query.budget,
  (budgetQuery) => {
    isBudgetEditorVisible.value = budgetQuery === "edit" && canEditBudget.value
  },
)

onMounted(async () => {
  await loadSelectedMonth()
  if (route.query.budget === "edit") {
    await nextTick()
    isBudgetEditorVisible.value = canEditBudget.value
  }
})
</script>

<template>
  <section class="expense-history-view">
    <AppPageHeader class="page-header" :title="`월별 리포트`">
      <template #leading>
        <RouterLink to="/assets" class="back-button" aria-label="자산 관리로 돌아가기">
          <i class="bi bi-chevron-left" aria-hidden="true"></i>
        </RouterLink>
      </template>
      <template #actions>
        <AppButton
          class="expense-sync-button"
          variant="primary"
          type="button"
          :disabled="isSyncing || isExpenseInitialLoading || isExpenseRefreshing || isDailyLoading"
          @click="syncCurrentMonth"
        >
          <span
            v-if="isSyncing"
            class="spinner-border spinner-border-sm me-2"
            aria-hidden="true"
          ></span>
          {{ isSyncing ? "동기화 중..." : "거래내역 새로고침" }}
        </AppButton>
      </template>
    </AppPageHeader>

    <AppAlert
      v-if="syncStatus"
      class="expense-sync-status"
      :variant="syncStatus.type"
      :message="syncStatus.message"
      role="status"
    />

    <div v-if="isExpenseRefreshing" class="small text-secondary mb-3" role="status">
      <span class="spinner-border spinner-border-sm text-primary me-2" aria-hidden="true"></span>
      {{ monthLabel }} 소비 내역을 최신 상태로 갱신하고 있습니다.
    </div>

    <AppAlert v-if="error && hasLoadedExpenseData" class="expense-error mb-3" variant="warning">
      <span>최신 소비 내역을 갱신하지 못했습니다. 기존 내역을 표시하고 있습니다.</span>
      <AppButton variant="outline" size="sm" @click="loadSelectedMonth">다시 시도</AppButton>
    </AppAlert>

    <AppState
      v-if="isExpenseInitialLoading"
      class="page-state"
      type="loading"
      title="소비 내역을 불러오는 중입니다."
      :message="`${monthLabel} 소비 내역을 불러오고 있습니다.`"
    />

    <AppAlert v-else-if="error && !hasLoadedExpenseData" class="expense-error" variant="danger">
      <div>
        <strong class="d-block mb-1">소비 내역을 불러오지 못했습니다.</strong>
        <span>{{ error }}</span>
      </div>
      <AppButton variant="outline" size="sm" @click="loadSelectedMonth">다시 시도</AppButton>
    </AppAlert>

    <template v-else>
      <AppCard class="history-card mb-4" padding="none">
        <div class="history-card-body">
          <div
            class="history-toolbar d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4"
          >
            <div class="history-view-controls d-flex flex-wrap align-items-center gap-3">
              <div class="month-navigation d-flex align-items-center gap-2">
                <button
                  type="button"
                  class="month-button"
                  aria-label="이전 달"
                  @click="moveMonth(-1)"
                >
                  <i class="bi bi-chevron-left" aria-hidden="true"></i>
                </button>
                <strong>{{ monthLabel }}</strong>
                <button
                  type="button"
                  class="month-button"
                  aria-label="다음 달"
                  @click="moveMonth(1)"
                >
                  <i class="bi bi-chevron-right" aria-hidden="true"></i>
                </button>
              </div>

              <div class="view-toggle btn-group" role="group" aria-label="소비 내역 보기 방식">
                <button
                  type="button"
                  class="btn"
                  :class="{ active: activeView === 'calendar' }"
                  :aria-pressed="activeView === 'calendar'"
                  @click="activeView = 'calendar'"
                >
                  <i class="bi bi-calendar3 me-1" aria-hidden="true"></i>
                  달력
                </button>
                <button
                  type="button"
                  class="btn"
                  :class="{ active: activeView === 'list' }"
                  :aria-pressed="activeView === 'list'"
                  @click="activeView = 'list'"
                >
                  <i class="bi bi-list-ul me-1" aria-hidden="true"></i>
                  리스트
                </button>
              </div>

              <div
                v-if="activeView === 'list'"
                class="category-filter d-flex align-items-center gap-2"
              >
                <button
                  type="button"
                  class="category-filter-trigger"
                  data-testid="open-category-filter"
                  aria-haspopup="dialog"
                  :disabled="isCategoryFilterSaving"
                  @click="openCategoryFilter"
                >
                  <span>카테고리 필터</span>
                  <span v-if="selectedListCategory !== 'ALL'" class="category-filter-current">
                    {{ selectedListCategoryLabel }}
                  </span>
                  <i class="bi bi-chevron-down" aria-hidden="true"></i>
                </button>
              </div>
            </div>

            <div class="monthly-summary d-flex flex-wrap gap-2">
              <span class="summary-pill income">
                수입 <strong>{{ formatWon(expenseData.totalIncome) }}</strong>
              </span>
              <span class="summary-pill expense">
                지출 <strong>{{ formatWon(expenseData.totalExpense) }}</strong>
              </span>
            </div>
          </div>

          <AppState
            v-if="!hasAnyData"
            class="page-state empty-state"
            type="empty"
            title="이 달의 소비 내역이 없습니다."
            message="다른 달로 이동해 수입과 지출 기록을 확인해 보세요."
          />

          <ExpenseCalendar
            v-else-if="activeView === 'calendar'"
            :month="selectedMonth"
            :daily-breakdown="expenseData.dailyBreakdown"
            @select-date="openDailyModal"
          />

          <ExpenseTransactionList
            v-else
            :transactions="expenseData.transactions"
            :editable="true"
            :has-next="expenseData.pagination.hasNext"
            :is-loading-more="isLoadingMore"
            :load-more-error="loadMoreError"
            @load-more="loadMore"
            @edit-category="openCategoryEditor"
          />
        </div>
      </AppCard>

    </template>

    <CategoryBudgetEditor
      :visible="isBudgetEditorVisible"
      :budget-summary="budgetSummary"
      :target-month="targetMonth"
      :is-saving="isBudgetSaving"
      @close="closeBudgetEditor"
      @save="saveBudget"
    />

    <ExpenseCategoryEditModal
      :visible="isCategoryEditModalVisible"
      :transaction="selectedTransaction"
      :is-saving="isCategorySaving"
      @close="closeCategoryEditor"
      @save="saveTransactionCategory"
    />

    <ExpenseCategoryEditModal
      :visible="isCategoryFilterModalVisible"
      mode="filter"
      :initial-category="selectedListCategory"
      :is-saving="isCategoryFilterSaving"
      @close="closeCategoryFilter"
      @save="applyCategoryFilter"
    />

    <AppDialog
      :visible="isDailyModalVisible"
      :title="selectedDateLabel"
      size="lg"
      confirm-text="닫기"
      confirm-variant="secondary"
      @close="closeDailyModal"
      @confirm="closeDailyModal"
    >
      <div class="daily-expense-modal-body">
        <AppState
          v-if="isDailyLoading"
          class="daily-modal-state"
          type="loading"
          title="일별 거래 내역을 불러오는 중입니다."
          message="잠시만 기다려 주세요."
          compact
        />
        <AppAlert v-else-if="dailyError" class="daily-modal-state" variant="danger">
          <span>{{ dailyError }}</span>
          <AppButton variant="outline" size="sm" @click="fetchDailyExpensePage(0)">
            다시 시도
          </AppButton>
        </AppAlert>
        <ExpenseTransactionList
          v-else
          :transactions="dailyTransactions"
          :has-next="dailyPagination.hasNext"
          :is-loading-more="isDailyLoadingMore"
          :load-more-error="dailyLoadMoreError"
          :editable="false"
          empty-message="선택한 날짜의 거래 내역이 없습니다."
          @load-more="loadMoreDailyExpenses"
        />
      </div>
    </AppDialog>
  </section>
</template>

<style scoped>
.expense-history-view {
  width: 100%;
  padding: 0 0 var(--wallo-space-6);
}

.expense-sync-button {
  min-width: 172px;
}

.expense-sync-status {
  margin-bottom: var(--wallo-space-4);
}

.back-button,
.month-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  color: #555b6e;
  background: transparent;
}

.back-button {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  font-size: 1.1rem;
  text-decoration: none;
}

.back-button:hover,
.back-button:focus,
.month-button:hover,
.month-button:focus {
  color: #6b5bd2;
  background: #f0edff;
}

.view-toggle {
  padding: 4px;
  border: 1px solid #e4e1f4;
  border-radius: 12px;
  background: #ffffff;
}

.view-toggle .btn {
  border: 0;
  border-radius: 9px !important;
  color: #777d90;
  font-size: 0.88rem;
  font-weight: 700;
}

.view-toggle .btn.active {
  color: #6b5bd2;
  background: #f0edff;
}

.category-filter-trigger {
  display: inline-flex;
  min-width: 142px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 11px;
  border: 1px solid #e4e1f4;
  border-radius: 10px;
  color: #343044;
  background: #ffffff;
  font-size: 0.88rem;
  font-weight: 700;
}

.category-filter-current {
  margin-left: 2px;
  color: #6b5bd2;
  font-size: 0.78rem;
}

.category-filter-trigger:hover:not(:disabled),
.category-filter-trigger:focus-visible {
  border-color: #b9b0f2;
  color: #6b5bd2;
  background: #faf9ff;
}

.category-filter-trigger:focus-visible {
  outline: 2px solid #6b5bd2;
  outline-offset: 2px;
}

.category-filter-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.history-card,
.page-state {
  border-radius: var(--wallo-radius-xl);
}

.history-card-body {
  padding: var(--wallo-space-6);
}

.month-navigation strong {
  min-width: 108px;
  color: #343044;
  text-align: center;
}

.month-button {
  width: 32px;
  height: 32px;
  border-radius: 10px;
}

.summary-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 11px;
  border-radius: 10px;
  font-size: 0.78rem;
  font-weight: 700;
}

.summary-pill.income {
  color: #4f73e8;
  background: #edf2ff;
}

.summary-pill.expense {
  color: #6f7587;
  background: #f1f3f6;
}

.page-state {
  display: flex;
  min-height: 390px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.empty-state {
  min-height: 360px;
  box-shadow: none;
}

.expense-error {
  display: flex;
  min-height: 110px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-radius: 18px;
}

.daily-expense-modal {
  max-width: 760px;
}

.daily-expense-modal-body {
  max-height: min(68vh, 650px);
  overflow-y: auto;
}

.daily-expense-modal-body :deep(.app-state),
.daily-expense-modal-body :deep(.app-alert) {
  min-height: 220px;
}

.daily-modal-state {
  display: flex;
  min-height: 230px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

@media (max-width: 575.98px) {
  .expense-history-view {
    padding-bottom: var(--wallo-space-5);
  }

  .page-header {
    align-items: stretch !important;
  }

  .history-card-body {
    padding: var(--wallo-space-5) var(--wallo-space-4);
  }

  .history-toolbar,
  .expense-error {
    align-items: stretch !important;
    flex-direction: column;
  }

  .month-navigation {
    justify-content: center;
  }

  .monthly-summary {
    justify-content: center;
  }

  .history-view-controls {
    justify-content: center;
  }

  .daily-expense-modal-body {
    padding-right: var(--wallo-space-1);
    padding-left: var(--wallo-space-1);
  }
}
</style>
