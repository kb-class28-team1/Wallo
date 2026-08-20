<script setup>
import { computed, onMounted, ref } from "vue"
import ExpenseTransactionList from "@/components/asset/ExpenseTransactionList.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"
import { getExpenses } from "@/api/assetApi"
import { getApiErrorMessage } from "@/utils/apiError"
import {
  getExpenseCategoryMeta,
  normalizeExpenseCategory,
} from "@/features/financial/financialCategories"
import { formatWon } from "@/utils/formatters"

const PAGE_SIZE = 20

const createEmptyExpenseData = () => ({
  totalExpense: 0,
  expenseCategoryBreakdown: [],
  transactions: [],
  pagination: {
    currentPage: 0,
    totalPages: 0,
    totalElements: 0,
    hasNext: false,
  },
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
  transactions: data?.transactions ?? [],
  pagination: {
    currentPage: Number(data?.pagination?.currentPage) || 0,
    totalPages: Number(data?.pagination?.totalPages) || 0,
    totalElements: Number(data?.pagination?.totalElements) || 0,
    hasNext: Boolean(data?.pagination?.hasNext),
  },
})

const selectedMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const selectedCategory = ref("ALL")
const expenseData = ref(createEmptyExpenseData())
const transactions = ref([])
const pagination = ref(createEmptyExpenseData().pagination)
const hasLoadedData = ref(false)
const isLoading = ref(false)
const isTransactionLoading = ref(false)
const error = ref("")
const transactionError = ref("")
let summaryRequestVersion = 0
let transactionRequestVersion = 0

const monthLabel = computed(() =>
  new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long" }).format(
    selectedMonth.value,
  ),
)

const dateRange = computed(() => {
  const year = selectedMonth.value.getFullYear()
  const month = selectedMonth.value.getMonth()
  return {
    startDate: formatDate(new Date(year, month, 1)),
    endDate: formatDate(new Date(year, month + 1, 0)),
  }
})

const categoryOptions = computed(() => {
  const amountByCategory = new Map()

  for (const item of expenseData.value.expenseCategoryBreakdown) {
    const category = normalizeExpenseCategory(item.category)
    amountByCategory.set(
      category,
      (amountByCategory.get(category) ?? 0) + (Number(item.amount) || 0),
    )
  }

  const categories = [...amountByCategory.entries()]
    .map(([category, amount]) => {
      const meta = getExpenseCategoryMeta(category)
      return {
        category,
        label: meta.label,
        amount,
        color: meta.color,
        icon: meta.icon,
      }
    })
    .filter((category) => category.amount > 0)
    .sort((first, second) => second.amount - first.amount)

  return [
    {
      category: "ALL",
      label: "전체",
      amount: expenseData.value.totalExpense,
      color: "#8170ff",
      icon: "bi-pie-chart",
    },
    ...categories,
  ]
})

const selectedCategoryLabel = computed(
  () =>
    categoryOptions.value.find((category) => category.category === selectedCategory.value)
      ?.label ?? "전체",
)

const hasCategoryData = computed(() => categoryOptions.value.length > 1)
const isInitialLoading = computed(() => isLoading.value && !hasLoadedData.value)
const isRefreshing = computed(() => isLoading.value && hasLoadedData.value)

const categoryRate = (amount) => {
  const totalExpense = Number(expenseData.value.totalExpense) || 0
  return totalExpense > 0 ? Math.round((Number(amount) / totalExpense) * 100) : 0
}

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
      throw new Error(response?.error?.message || "카테고리별 소비내역 응답이 올바르지 않습니다.")
    }

    const nextData = normalizeExpenseData(response.data)
    expenseData.value = nextData
    transactions.value = nextData.transactions
    pagination.value = nextData.pagination
    hasLoadedData.value = true
  } catch (caughtError) {
    if (currentRequest !== summaryRequestVersion) return

    const message = getApiErrorMessage(
      caughtError,
      "카테고리별 소비 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
    )
    error.value = message
    if (isInitialLoad) {
      expenseData.value = createEmptyExpenseData()
      transactions.value = []
      pagination.value = createEmptyExpenseData().pagination
    }
    alert(message)
  } finally {
    if (currentRequest === summaryRequestVersion) {
      isLoading.value = false
    }
  }
}

const loadTransactions = async (page = 0, append = false) => {
  const currentRequest = ++transactionRequestVersion
  if (append) {
    transactionError.value = ""
  } else {
    isTransactionLoading.value = true
    transactionError.value = ""
  }

  try {
    const response = await getExpenses({
      ...dateRange.value,
      page,
      size: PAGE_SIZE,
      ...(selectedCategory.value !== "ALL" ? { category: selectedCategory.value } : {}),
    })

    if (currentRequest !== transactionRequestVersion) return
    if (!response?.success || !response?.data) {
      throw new Error(response?.error?.message || "거래내역 응답이 올바르지 않습니다.")
    }

    const nextData = normalizeExpenseData(response.data)
    transactions.value = append
      ? [...transactions.value, ...nextData.transactions]
      : nextData.transactions
    pagination.value = nextData.pagination
  } catch (caughtError) {
    if (currentRequest !== transactionRequestVersion) return

    const message = getApiErrorMessage(
      caughtError,
      "거래내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
    )
    transactionError.value = message
    if (!append) {
      transactions.value = []
      pagination.value = createEmptyExpenseData().pagination
    }
    alert(message)
  } finally {
    if (currentRequest === transactionRequestVersion) {
      isTransactionLoading.value = false
    }
  }
}

const loadSelectedMonth = async () => {
  transactionRequestVersion += 1
  selectedCategory.value = "ALL"
  isTransactionLoading.value = false
  transactionError.value = ""
  await loadSummary()
}

const selectCategory = async (category) => {
  if (isTransactionLoading.value || selectedCategory.value === category) return
  selectedCategory.value = category
  await loadTransactions()
}

const moveMonth = async (offset) => {
  const year = selectedMonth.value.getFullYear()
  const month = selectedMonth.value.getMonth()
  selectedMonth.value = new Date(year, month + offset, 1)
  await loadSelectedMonth()
}

const loadMore = async () => {
  if (isTransactionLoading.value || !pagination.value.hasNext) return
  await loadTransactions(pagination.value.currentPage + 1, true)
}

onMounted(loadSelectedMonth)
</script>

<template>
  <section class="category-expense-view">
    <AppPageHeader class="page-header" title="카테고리별 소비 내역">
      <template #leading>
        <RouterLink to="/assets" class="back-button" aria-label="자산 관리로 돌아가기">
          <i class="bi bi-chevron-left" aria-hidden="true"></i>
        </RouterLink>
      </template>
    </AppPageHeader>

    <div v-if="isRefreshing" class="small text-secondary mb-3" role="status">
      <span class="spinner-border spinner-border-sm text-primary me-2" aria-hidden="true"></span>
      {{ monthLabel }} 소비 내역을 최신 상태로 갱신하고 있습니다.
    </div>

    <AppState
      v-if="isInitialLoading"
      class="page-state"
      type="loading"
      title="카테고리별 소비 내역을 불러오는 중입니다."
      message="잠시만 기다려 주세요."
    />

    <AppAlert v-else-if="error && !hasLoadedData" class="page-state" variant="danger">
      <div>
        <strong class="d-block mb-1">소비 내역을 불러오지 못했습니다.</strong>
        <span>{{ error }}</span>
      </div>
      <button type="button" class="btn btn-outline-danger btn-sm" @click="loadSelectedMonth">
        다시 시도
      </button>
    </AppAlert>

    <template v-else>
      <AppAlert v-if="error && hasLoadedData" class="mb-3" variant="warning">
        <span>최신 소비 내역을 갱신하지 못했습니다. 기존 내역을 표시하고 있습니다.</span>
        <button type="button" class="btn btn-outline-warning btn-sm" @click="loadSelectedMonth">
          다시 시도
        </button>
      </AppAlert>

      <AppCard class="category-expense-card" padding="none">
        <div class="category-expense-body">
          <div class="category-expense-toolbar d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4">
            <div class="month-navigation d-flex align-items-center gap-2">
              <button type="button" class="month-button" aria-label="이전 달" @click="moveMonth(-1)">
                <i class="bi bi-chevron-left" aria-hidden="true"></i>
              </button>
              <strong>{{ monthLabel }}</strong>
              <button type="button" class="month-button" aria-label="다음 달" @click="moveMonth(1)">
                <i class="bi bi-chevron-right" aria-hidden="true"></i>
              </button>
            </div>
            <span class="summary-pill expense">
              총 지출 <strong>{{ formatWon(expenseData.totalExpense) }}</strong>
            </span>
          </div>

          <AppState
            v-if="!hasCategoryData"
            class="page-state empty-state"
            type="empty"
            title="표시할 소비 카테고리가 없습니다."
            message="거래내역이 쌓이면 카테고리별 소비 내역을 확인할 수 있습니다."
          />

          <div v-else class="row g-4">
            <div class="col-12 col-lg-5">
              <h2 class="h5 fw-bold mb-3">카테고리</h2>
              <div class="category-option-list" role="listbox" aria-label="소비 카테고리 선택">
                <button
                  v-for="category in categoryOptions"
                  :key="category.category"
                  type="button"
                  class="category-option d-flex align-items-center justify-content-between gap-3"
                  :class="{ active: selectedCategory === category.category }"
                  :data-testid="`category-option-${category.category}`"
                  :aria-selected="selectedCategory === category.category"
                  :disabled="isTransactionLoading"
                  @click="selectCategory(category.category)"
                >
                  <span class="category-option-label d-flex align-items-center gap-2">
                    <span
                      class="category-icon"
                      :style="{ color: category.color, backgroundColor: `${category.color}18` }"
                    >
                      <i :class="['bi', category.icon]" aria-hidden="true"></i>
                    </span>
                    {{ category.label }}
                  </span>
                  <span class="category-option-value text-end">
                    <strong>{{ formatWon(category.amount) }}</strong>
                    <small>{{ categoryRate(category.amount) }}%</small>
                  </span>
                </button>
              </div>
            </div>

            <div class="col-12 col-lg-7">
              <div class="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
                <div>
                  <h2 class="h5 fw-bold mb-1">{{ selectedCategoryLabel }} 거래내역</h2>
                  <p class="text-secondary small mb-0">선택한 월의 소비 내역을 확인하세요.</p>
                </div>
                <span v-if="isTransactionLoading" class="small text-secondary" role="status">
                  <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
                  불러오는 중
                </span>
              </div>

              <AppAlert v-if="transactionError" class="mb-3" variant="danger">
                <span>{{ transactionError }}</span>
                <button type="button" class="btn btn-outline-danger btn-sm" @click="loadTransactions()">
                  다시 시도
                </button>
              </AppAlert>

              <ExpenseTransactionList
                :transactions="transactions"
                :editable="false"
                :has-next="pagination.hasNext"
                :is-loading-more="isTransactionLoading"
                :load-more-error="transactionError && pagination.hasNext ? transactionError : ''"
                empty-message="선택한 카테고리의 거래내역이 없습니다."
                @load-more="loadMore"
              />
            </div>
          </div>
        </div>
      </AppCard>
    </template>
  </section>
</template>

<style scoped>
.category-expense-view {
  width: 100%;
  padding: var(--wallo-space-6) var(--wallo-space-4);
}

.category-expense-card {
  border-radius: var(--wallo-radius-xl);
}

.category-expense-body {
  padding: var(--wallo-space-6);
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
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: var(--wallo-radius-sm);
  color: var(--wallo-color-text-muted);
  background: var(--wallo-color-surface);
}

.month-button:hover,
.month-button:focus-visible {
  border-color: var(--wallo-color-primary);
  color: var(--wallo-color-primary);
}

.month-button:focus-visible,
.category-option:focus-visible {
  outline: 2px solid var(--wallo-color-primary);
  outline-offset: 2px;
}

.summary-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--wallo-radius-pill);
  font-size: 0.875rem;
  font-weight: 700;
}

.summary-pill.expense {
  color: var(--wallo-color-finance-decrease);
  background: #fff0ed;
}

.category-option-list {
  display: grid;
  gap: 10px;
}

.category-option {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: var(--wallo-radius-md);
  color: var(--wallo-color-text);
  background: var(--wallo-color-surface);
  text-align: left;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.category-option:hover:not(:disabled),
.category-option.active {
  border-color: var(--wallo-color-primary);
  background: #f7f5ff;
}

.category-option:disabled {
  cursor: wait;
  opacity: 0.7;
}

.category-icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
}

.category-option-value {
  display: grid;
  gap: 2px;
}

.category-option-value strong {
  white-space: nowrap;
}

.category-option-value small {
  color: var(--wallo-color-text-muted);
}

.page-state {
  min-height: 260px;
}

.empty-state {
  min-height: 320px;
}

@media (max-width: 575.98px) {
  .category-expense-view {
    padding-right: var(--wallo-space-3);
    padding-left: var(--wallo-space-3);
  }

  .category-expense-body {
    padding: var(--wallo-space-4);
  }
}
</style>
