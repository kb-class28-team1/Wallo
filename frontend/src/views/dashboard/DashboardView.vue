<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue"
import { storeToRefs } from "pinia"
import { useRouter } from "vue-router"
import AssetSummaryCard from "@/components/dashboard/AssetSummaryCard.vue"
import BudgetSummaryCard from "@/components/dashboard/BudgetSummaryCard.vue"
import ExpenseSummaryCard from "@/components/dashboard/ExpenseSummaryCard.vue"
import GoalSummaryCard from "@/components/dashboard/GoalSummaryCard.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"
import { useDashboardCharts } from "@/features/financial/useDashboardCharts"
import { useDashboardStore } from "@/stores/useDashboardStore"
import { useGoalStore } from "@/stores/goalStore"
import { useUserStore } from "@/stores/userStore"

const dashboardStore = useDashboardStore()
const goalStore = useGoalStore()
const userStore = useUserStore()
const router = useRouter()
const {
  initialLoading,
  refreshing,
  assets,
  assetLoading,
  assetError,
  hasAssetData,
  budget,
  budgetLoading,
  budgetError,
  hasBudgetData,
  expenses,
  expenseLoading,
  expenseError,
  hasExpenseData,
  error,
} = storeToRefs(dashboardStore)
const {
  goals,
  initialLoading: isGoalInitialLoading,
  refreshing: isGoalRefreshing,
  error: goalError,
  availableAccounts,
  isAccountLoading,
} = storeToRefs(goalStore)
const { user } = storeToRefs(userStore)
const { assetTrendChartData, expenseChartData } = useDashboardCharts(assets, expenses)

const GOAL_REFRESH_INTERVAL_MS = 5 * 60 * 1000
const isDashboardReady = ref(false)
const hadDashboardDataBeforeLoad = ref(false)
const userId = computed(() => user.value?.id ?? null)
let goalRefreshTimer = null
let goalRefreshInFlight = null

const hasFinancialDashboardData = computed(() =>
  Boolean(hasAssetData.value || hasBudgetData.value || hasExpenseData.value),
)

const hasRenderedDashboardData = computed(() =>
  Boolean(hasFinancialDashboardData.value || goals.value.length > 0),
)

const isDashboardInitialLoading = computed(
  () =>
    !hadDashboardDataBeforeLoad.value &&
    (!isDashboardReady.value || initialLoading.value || isGoalInitialLoading.value),
)

const isDashboardRefreshing = computed(() => refreshing.value || isGoalRefreshing.value)

const hasDashboardData = computed(() =>
  Boolean(hasRenderedDashboardData.value || isGoalInitialLoading.value || goalError.value),
)

const handleBudgetSettings = async () => {
  await router.push({
    name: "category-expenses",
    query: { budget: "edit" },
  })
}

const handleGoalRetry = () => {
  goalStore.fetchGoals({
    userId: userId.value,
    force: true,
    syncAccounts: false,
  })
}

const refreshGoalData = ({ refreshDashboard = false } = {}) => {
  if (goalRefreshInFlight) {
    return goalRefreshInFlight
  }

  goalRefreshInFlight = (async () => {
    const goalRequest = goalStore.fetchGoals({
      userId: userId.value,
      notifyError: false,
      syncAccounts: false,
    })
    const accountRequest = goalRequest.then((loadedGoals) => {
      if (!loadedGoals.length) {
        return []
      }

      return goalStore.fetchAvailableAccounts({ notifyError: false })
    })

    if (refreshDashboard) {
      await Promise.all([accountRequest, dashboardStore.fetchDashboardSummary()])
      return
    }
    await accountRequest
  })().finally(() => {
    goalRefreshInFlight = null
  })

  return goalRefreshInFlight
}

const loadDashboard = async () => {
  hadDashboardDataBeforeLoad.value = hasRenderedDashboardData.value
  isDashboardReady.value = false
  // 대시보드는 저장된 데이터를 먼저 읽고, CODEF 동기화와 분리해 렌더링한다.
  try {
    await Promise.all([refreshGoalData(), dashboardStore.fetchDashboardSummary()])
  } finally {
    isDashboardReady.value = true
  }
}

const handleVisibilityChange = () => {
  if (document.visibilityState === "visible") {
    refreshGoalData({ refreshDashboard: true })
  }
}

onMounted(() => {
  loadDashboard()
  goalRefreshTimer = window.setInterval(
    () => refreshGoalData({ refreshDashboard: true }),
    GOAL_REFRESH_INTERVAL_MS,
  )
  document.addEventListener("visibilitychange", handleVisibilityChange)
})

onBeforeUnmount(() => {
  if (goalRefreshTimer !== null) {
    window.clearInterval(goalRefreshTimer)
    goalRefreshTimer = null
  }
  document.removeEventListener("visibilitychange", handleVisibilityChange)
})
</script>

<template>
  <section class="dashboard-page">
    <AppState
      v-if="isDashboardInitialLoading"
      class="dashboard-state"
      type="loading"
      title="대시보드 데이터를 불러오는 중입니다."
      message="잠시만 기다려 주세요."
    />

    <AppAlert
      v-else-if="error && !hasRenderedDashboardData"
      class="dashboard-state"
      variant="danger"
      :message="error"
    />

    <AppState
      v-else-if="!hasDashboardData"
      class="dashboard-state"
      type="empty"
      title="표시할 대시보드 데이터가 없습니다."
      message="연결된 자산과 목표를 추가하면 이곳에 표시됩니다."
    />

    <div v-else>
      <AppPageHeader title="대시보드">
        <template #actions>
          <span v-if="isDashboardRefreshing" class="dashboard-refresh-status" role="status">
            <span
              class="spinner-border spinner-border-sm text-primary me-2"
              aria-hidden="true"
            ></span>
            최신 정보 갱신 중
          </span>
        </template>
      </AppPageHeader>
      <AppAlert
        v-if="error"
        class="dashboard-sync-alert"
        variant="warning"
        message="최신 대시보드 정보를 갱신하지 못했습니다. 기존 정보를 표시하고 있습니다."
      />

      <div class="dashboard-card-grid">
        <AssetSummaryCard
          v-if="hasAssetData"
          :assets="assets"
          :chart-data="assetTrendChartData"
        />
        <AppState
          v-else-if="assetLoading"
          class="dashboard-resource-state"
          type="loading"
          title="자산 정보를 불러오는 중입니다."
          message="잠시만 기다려 주세요."
          compact
        />
        <AppState
          v-else-if="assetError"
          class="dashboard-resource-state"
          type="error"
          title="자산 정보를 불러오지 못했습니다."
          :message="assetError"
          compact
        />
        <AppState
          v-else
          class="dashboard-resource-state"
          type="empty"
          title="연결된 자산이 없습니다."
          message="금융기관을 연결하면 자산 현황을 확인할 수 있습니다."
          compact
        />

        <BudgetSummaryCard
          v-if="hasBudgetData || (!budgetLoading && !budgetError)"
          :budget="budget"
          @open-budget-settings="handleBudgetSettings"
        />
        <AppState
          v-else-if="budgetLoading"
          class="dashboard-resource-state"
          type="loading"
          title="예산 정보를 불러오는 중입니다."
          message="잠시만 기다려 주세요."
          compact
        />
        <AppState
          v-else
          class="dashboard-resource-state"
          type="error"
          title="예산 정보를 불러오지 못했습니다."
          :message="budgetError"
          compact
        />
      </div>

      <div class="dashboard-summary-grid">
        <ExpenseSummaryCard
          v-if="hasExpenseData"
          :expenses="expenses"
          :chart-data="expenseChartData"
        />
        <AppState
          v-else-if="expenseLoading"
          class="dashboard-resource-state"
          type="loading"
          title="소비 정보를 불러오는 중입니다."
          message="잠시만 기다려 주세요."
          compact
        />
        <AppState
          v-else-if="expenseError"
          class="dashboard-resource-state"
          type="error"
          title="소비 정보를 불러오지 못했습니다."
          :message="expenseError"
          compact
        />
        <AppState
          v-else
          class="dashboard-resource-state"
          type="empty"
          title="이번 달 지출 데이터가 없습니다."
          message="지출 내역이 등록되면 카테고리별 현황을 확인할 수 있습니다."
          compact
        />
        <GoalSummaryCard
          :goals="goals"
          :loading="isGoalInitialLoading"
          :error="goalError"
          :available-accounts="availableAccounts"
          :accounts-loading="isAccountLoading"
          @retry="handleGoalRetry"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.dashboard-page {
  width: 100%;
}

.dashboard-state {
  min-height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.dashboard-refresh-status {
  display: inline-flex;
  align-items: center;
  color: var(--wallo-color-text-muted);
  font-size: 0.875rem;
}

.dashboard-sync-alert {
  margin-bottom: var(--wallo-space-4);
}

.dashboard-resource-state {
  min-height: 294px;
}

.dashboard-card-grid {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 3fr);
  gap: var(--wallo-space-5);
  max-width: var(--wallo-content-max-width);
  margin-top: 0;
}

.dashboard-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--wallo-space-5);
  max-width: var(--wallo-content-max-width);
  margin-top: var(--wallo-space-5);
}

@media (max-width: 991.98px) {
  .dashboard-card-grid {
    grid-template-columns: 1fr;
    gap: var(--wallo-space-5);
    margin-top: var(--wallo-space-5);
  }

  .dashboard-summary-grid {
    grid-template-columns: 1fr;
    gap: var(--wallo-space-5);
    margin-top: var(--wallo-space-5);
  }
}
</style>
