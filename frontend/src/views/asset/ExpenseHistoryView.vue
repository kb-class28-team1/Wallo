<script setup>
import { computed, onMounted, ref } from "vue";
import { storeToRefs } from "pinia";
import ExpenseCalendar from "@/components/asset/ExpenseCalendar.vue";
import ExpenseCategoryBreakdown from "@/components/asset/ExpenseCategoryBreakdown.vue";
import ExpenseTransactionList from "@/components/asset/ExpenseTransactionList.vue";
import { getExpenses } from "@/api/assetApi";
import { getApiErrorMessage } from "@/commonUtils/apiError";
import { formatWon } from "@/commonUtils/formatters";
import { useAssetStore } from "@/stores/assetStore";

const PAGE_SIZE = 20;
const assetStore = useAssetStore();
const { isSyncing, syncError } = storeToRefs(assetStore);

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
});

const now = new Date();
const selectedMonth = ref(new Date(now.getFullYear(), now.getMonth(), 1));
const activeView = ref("calendar");
const expenseData = ref(createEmptyExpenseData());
const isLoading = ref(false);
const isLoadingMore = ref(false);
const error = ref("");
const loadMoreError = ref("");
const isDailyModalVisible = ref(false);
const selectedDate = ref("");
const dailyTransactions = ref([]);
const dailyPagination = ref(createEmptyExpenseData().pagination);
const isDailyLoading = ref(false);
const isDailyLoadingMore = ref(false);
const dailyError = ref("");
const dailyLoadMoreError = ref("");
const syncStatus = ref(null);
let requestVersion = 0;
let dailyRequestVersion = 0;

const monthLabel = computed(() =>
  new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long" }).format(
    selectedMonth.value,
  ),
);

const dateRange = computed(() => {
  const year = selectedMonth.value.getFullYear();
  const month = selectedMonth.value.getMonth();
  return {
    startDate: formatDate(new Date(year, month, 1)),
    endDate: formatDate(new Date(year, month + 1, 0)),
  };
});

const hasAnyData = computed(() =>
  Number(expenseData.value.totalExpense) > 0 ||
  Number(expenseData.value.totalIncome) > 0 ||
  expenseData.value.transactions.length > 0,
);

const summaryDescription = computed(() =>
  `${monthLabel.value}의 수입과 지출 기록을 확인하세요.`,
);

const selectedDateLabel = computed(() => {
  if (!selectedDate.value) return "";
  const [year, month, day] = selectedDate.value.split("-").map(Number);
  return `${year}년 ${month}월 ${day}일`;
});

function formatDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
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
});

const fetchExpensePage = async (page, append = false) => {
  const currentRequest = ++requestVersion;
  if (append) {
    isLoadingMore.value = true;
    loadMoreError.value = "";
  } else {
    isLoading.value = true;
    error.value = "";
  }

  try {
    const response = await getExpenses({
      ...dateRange.value,
      page,
      size: PAGE_SIZE,
    });

    if (currentRequest !== requestVersion) return;
    if (!response?.success || !response?.data) {
      throw new Error(response?.error?.message || "소비 내역 응답이 올바르지 않습니다.");
    }

    const nextData = normalizeExpenseData(response.data);
    if (append) {
      nextData.transactions = [
        ...expenseData.value.transactions,
        ...nextData.transactions,
      ];
    }
    expenseData.value = nextData;
  } catch (caughtError) {
    if (currentRequest !== requestVersion) return;

    const message = getApiErrorMessage(
      caughtError,
      append
        ? "추가 거래 내역을 불러오지 못했습니다. 다시 시도해 주세요."
        : "소비 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );

    if (append) {
      loadMoreError.value = message;
    } else {
      expenseData.value = createEmptyExpenseData();
      error.value = message;
    }
    alert(message);
  } finally {
    if (currentRequest === requestVersion) {
      isLoading.value = false;
      isLoadingMore.value = false;
    }
  }
};

const loadSelectedMonth = async () => {
  requestVersion += 1;
  isLoadingMore.value = false;
  loadMoreError.value = "";
  expenseData.value = createEmptyExpenseData();
  await fetchExpensePage(0);
};

const syncCurrentMonth = async () => {
  if (isSyncing.value) return;

  syncStatus.value = null;
  closeDailyModal();

  try {
    const result = await assetStore.syncAssets();
    if (!result) return;

    await loadSelectedMonth();
    if (error.value) {
      syncStatus.value = {
        type: "warning",
        message: "동기화는 완료되었지만 현재 월 거래내역을 다시 불러오지 못했습니다.",
      };
      return;
    }

    const failedConnections = Number(result.failedConnections) || 0;
    const summary = `신규 ${Number(result.inserted) || 0}건, 수정 ${Number(result.updated) || 0}건`;
    syncStatus.value = failedConnections > 0
      ? {
          type: "warning",
          message: `동기화가 완료되었습니다. ${summary}, 실패한 연결기관 ${failedConnections}건`,
        }
      : {
          type: "success",
          message: `동기화가 완료되었습니다. ${summary}`,
        };
  } catch {
    syncStatus.value = {
      type: "danger",
      message: syncError.value || "자산 거래내역 동기화에 실패했습니다.",
    };
  }
};

const closeDailyModal = () => {
  dailyRequestVersion += 1;
  isDailyModalVisible.value = false;
  selectedDate.value = "";
  dailyTransactions.value = [];
  dailyPagination.value = createEmptyExpenseData().pagination;
  dailyError.value = "";
  dailyLoadMoreError.value = "";
  isDailyLoading.value = false;
  isDailyLoadingMore.value = false;
};

const fetchDailyExpensePage = async (page, append = false) => {
  const currentRequest = ++dailyRequestVersion;
  if (append) {
    isDailyLoadingMore.value = true;
    dailyLoadMoreError.value = "";
  } else {
    isDailyLoading.value = true;
    dailyError.value = "";
  }

  try {
    const response = await getExpenses({
      startDate: selectedDate.value,
      endDate: selectedDate.value,
      page,
      size: PAGE_SIZE,
    });

    if (currentRequest !== dailyRequestVersion) return;
    if (!response?.success || !response?.data) {
      throw new Error(response?.error?.message || "선택한 날짜의 응답이 올바르지 않습니다.");
    }

    const nextData = normalizeExpenseData(response.data);
    const dailyTransactionsPage = nextData.transactions.filter(
      (transaction) => ["EXPENSE", "INCOME", "TRANSFER"].includes(transaction.type),
    );
    dailyTransactions.value = append
      ? [...dailyTransactions.value, ...dailyTransactionsPage]
      : dailyTransactionsPage;
    dailyPagination.value = nextData.pagination;
  } catch (caughtError) {
    if (currentRequest !== dailyRequestVersion) return;
    const message = getApiErrorMessage(
      caughtError,
      append
        ? "추가 거래 내역을 불러오지 못했습니다. 다시 시도해 주세요."
        : "선택한 날짜의 거래 내역을 불러오지 못했습니다.",
    );

    if (append) {
      dailyLoadMoreError.value = message;
    } else {
      dailyTransactions.value = [];
      dailyPagination.value = createEmptyExpenseData().pagination;
      dailyError.value = message;
    }
    alert(message);
  } finally {
    if (currentRequest === dailyRequestVersion) {
      isDailyLoading.value = false;
      isDailyLoadingMore.value = false;
    }
  }
};

const openDailyModal = async (date) => {
  selectedDate.value = date;
  dailyTransactions.value = [];
  dailyPagination.value = createEmptyExpenseData().pagination;
  dailyLoadMoreError.value = "";
  isDailyModalVisible.value = true;
  await fetchDailyExpensePage(0);
};

const loadMoreDailyExpenses = async () => {
  if (isDailyLoadingMore.value || !dailyPagination.value.hasNext) return;
  await fetchDailyExpensePage(dailyPagination.value.currentPage + 1, true);
};

const moveMonth = async (offset) => {
  closeDailyModal();
  const year = selectedMonth.value.getFullYear();
  const month = selectedMonth.value.getMonth();
  selectedMonth.value = new Date(year, month + offset, 1);
  await loadSelectedMonth();
};

const loadMore = async () => {
  if (isLoadingMore.value || !expenseData.value.pagination.hasNext) return;
  await fetchExpensePage(expenseData.value.pagination.currentPage + 1, true);
};

onMounted(loadSelectedMonth);
</script>

<template>
  <section class="expense-history-view container-fluid py-4 px-4">
    <header class="page-header d-flex flex-wrap align-items-start justify-content-between gap-3 mb-4">
      <div class="d-flex align-items-start gap-3">
        <RouterLink to="/assets" class="back-button" aria-label="자산 관리로 돌아가기">
          <i class="bi bi-chevron-left" aria-hidden="true"></i>
        </RouterLink>
        <div>
          <h1 class="h3 fw-bold mb-1">월별 리포트</h1>
          <p class="text-secondary mb-0">{{ summaryDescription }}</p>
        </div>
      </div>

      <button
        type="button"
        class="btn btn-primary expense-sync-button"
        :disabled="isSyncing || isLoading || isDailyLoading"
        @click="syncCurrentMonth"
      >
        <span
          v-if="isSyncing"
          class="spinner-border spinner-border-sm me-2"
          aria-hidden="true"
        ></span>
        {{ isSyncing ? "동기화 중..." : "거래내역 새로고침" }}
      </button>
    </header>

    <div
      v-if="syncStatus"
      class="alert"
      :class="`alert-${syncStatus.type}`"
      role="status"
    >
      {{ syncStatus.message }}
    </div>

    <div v-if="isLoading" class="page-state card border-0 shadow-sm" aria-live="polite">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">소비 내역을 불러오는 중</span>
      </div>
      <p class="text-secondary mb-0 mt-3">{{ monthLabel }} 소비 내역을 불러오고 있습니다.</p>
    </div>

    <div v-else-if="error" class="alert alert-danger expense-error" role="alert">
      <div>
        <h2 class="h6 fw-bold mb-1">소비 내역을 불러오지 못했습니다.</h2>
        <p class="mb-0">{{ error }}</p>
      </div>
      <button type="button" class="btn btn-outline-danger flex-shrink-0" @click="loadSelectedMonth">
        다시 시도
      </button>
    </div>

    <template v-else>
      <article class="card history-card border-0 shadow-sm mb-4">
        <div class="card-body history-card-body">
          <div class="history-toolbar d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4">
            <div class="history-view-controls d-flex flex-wrap align-items-center gap-3">
              <div class="month-navigation d-flex align-items-center gap-2">
                <button type="button" class="month-button" aria-label="이전 달" @click="moveMonth(-1)">
                  <i class="bi bi-chevron-left" aria-hidden="true"></i>
                </button>
                <strong>{{ monthLabel }}</strong>
                <button type="button" class="month-button" aria-label="다음 달" @click="moveMonth(1)">
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

          <div v-if="!hasAnyData" class="page-state empty-state">
            <i class="bi bi-calendar2-x text-secondary fs-1" aria-hidden="true"></i>
            <h2 class="h5 fw-bold mb-1 mt-3">이 달의 소비 내역이 없습니다.</h2>
            <p class="text-secondary mb-0">다른 달로 이동해 수입과 지출 기록을 확인해 보세요.</p>
          </div>

          <ExpenseCalendar
            v-else-if="activeView === 'calendar'"
            :month="selectedMonth"
            :daily-breakdown="expenseData.dailyBreakdown"
            @select-date="openDailyModal"
          />

          <ExpenseTransactionList
            v-else
            :transactions="expenseData.transactions"
            :has-next="expenseData.pagination.hasNext"
            :is-loading-more="isLoadingMore"
            :load-more-error="loadMoreError"
            @load-more="loadMore"
          />
        </div>
      </article>

      <ExpenseCategoryBreakdown
        :breakdown="expenseData.expenseCategoryBreakdown"
        :total-expense="expenseData.totalExpense"
      />
    </template>

    <div
      v-if="isDailyModalVisible"
      class="modal-backdrop fade show"
      @click="closeDailyModal"
    ></div>
    <div
      v-if="isDailyModalVisible"
      class="modal fade show d-block"
      tabindex="-1"
      role="dialog"
      aria-modal="true"
      aria-labelledby="dailyExpenseModalTitle"
      @keydown.esc="closeDailyModal"
    >
      <div class="modal-dialog modal-dialog-centered modal-lg">
        <div class="modal-content daily-expense-modal">
          <div class="modal-header">
            <div>
              <h2 id="dailyExpenseModalTitle" class="modal-title h5 fw-bold mb-1">
                {{ selectedDateLabel }}
              </h2>
            </div>
            <button
              type="button"
              class="btn-close"
              aria-label="닫기"
              @click="closeDailyModal"
            ></button>
          </div>
          <div class="modal-body daily-expense-modal-body">
            <div v-if="isDailyLoading" class="daily-modal-state" aria-live="polite">
              <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">일별 거래 내역을 불러오는 중</span>
              </div>
              <p class="text-secondary mb-0 mt-3">거래 내역을 불러오고 있습니다.</p>
            </div>
            <div v-else-if="dailyError" class="daily-modal-state">
              <i class="bi bi-exclamation-circle text-danger fs-2" aria-hidden="true"></i>
              <p class="text-danger mb-3 mt-2">{{ dailyError }}</p>
              <button type="button" class="btn btn-outline-danger" @click="fetchDailyExpensePage(0)">
                다시 시도
              </button>
            </div>
            <ExpenseTransactionList
              v-else
              :transactions="dailyTransactions"
              :has-next="dailyPagination.hasNext"
              :is-loading-more="isDailyLoadingMore"
              :load-more-error="dailyLoadMoreError"
              empty-message="선택한 날짜의 거래 내역이 없습니다."
              @load-more="loadMoreDailyExpenses"
            />
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.expense-history-view {
  width: 100%;
}

.expense-sync-button {
  min-width: 172px;
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

.history-card,
.page-state {
  border-radius: 28px;
  background: #ffffff;
}

.history-card-body {
  padding: 32px 36px;
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
  overflow: hidden;
  border: 0;
  border-radius: 24px;
}

.daily-expense-modal .modal-header {
  padding: 24px 28px;
  border-bottom-color: #edf0f5;
}

.daily-expense-modal-body {
  max-height: min(68vh, 650px);
  overflow-y: auto;
  padding: 24px 28px 28px;
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
    padding-right: 0 !important;
    padding-left: 0 !important;
  }

  .page-header {
    align-items: stretch !important;
  }

  .history-card-body {
    padding: 26px 20px;
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

  .daily-expense-modal .modal-header,
  .daily-expense-modal-body {
    padding-right: 20px;
    padding-left: 20px;
  }
}
</style>
