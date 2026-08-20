<script setup>
import { computed, onMounted, ref, watch } from "vue"
import { getPointHistory } from "@/api/pointHistoryApi"
import { getCachedResource, getResource, hasInFlightResource } from "@/utils/resourceCache"
import { useUserStore } from "@/stores/userStore"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"

const userStore = useUserStore()
const activeType = ref("ALL")
const activePeriod = ref("ALL")
const sort = ref("LATEST")
const keyword = ref("")
const page = ref(0)
const size = ref(20)
const initialLoading = ref(true)
const refreshing = ref(false)
const hasLoadedHistory = ref(false)
const errorMessage = ref("")
const summary = ref({
  totalEarned: 0,
  totalUsed: 0,
  balance: 0,
  monthlyChange: 0,
})
const items = ref([])
const totalPages = ref(0)

const tabs = [
  { value: "ALL", label: "전체" },
  { value: "EARN", label: "적립" },
  { value: "USE", label: "사용" },
]

const periods = [
  { value: "ALL", label: "전체" },
  { value: "THIS_MONTH", label: "이번 달" },
  { value: "LAST_3_MONTHS", label: "지난 3개월" },
]

const formattedSummary = computed(() => ({
  totalEarned: formatPoint(summary.value.totalEarned),
  totalUsed: formatPoint(summary.value.totalUsed),
  balance: formatPoint(summary.value.balance),
  monthlyChange: formatPoint(summary.value.monthlyChange, true),
}))

const extractPayload = (response) => response?.data?.data || response?.data || response

const pointHistoryCacheKey = () =>
  [
    "point-history",
    userStore.user?.id ?? "current",
    activeType.value,
    activePeriod.value,
    sort.value,
    keyword.value.trim(),
    page.value,
    size.value,
  ].join(":")

const applyHistory = (payload) => {
  summary.value = {
    ...summary.value,
    ...(payload?.summary || {}),
  }
  items.value = Array.isArray(payload?.items) ? payload.items : []
  totalPages.value = Number(payload?.totalPages || 0)
  hasLoadedHistory.value = true
  return payload
}

const loadHistory = async ({ force = false } = {}) => {
  const key = pointHistoryCacheKey()
  const cachedHistory =
    !force && !hasInFlightResource(key)
      ? getCachedResource(key, { staleTime: 60 * 1000 })
      : undefined

  if (cachedHistory !== undefined) {
    errorMessage.value = ""
    initialLoading.value = false
    refreshing.value = false
    return applyHistory(cachedHistory)
  }

  const isInitialLoad = !hasLoadedHistory.value
  initialLoading.value = isInitialLoad
  refreshing.value = !isInitialLoad
  errorMessage.value = ""

  try {
    const payload = await getResource(
      key,
      async () => {
        const response = await getPointHistory({
          type: activeType.value,
          period: activePeriod.value,
          sort: sort.value,
          keyword: keyword.value.trim() || undefined,
          page: page.value,
          size: size.value,
        })
        return extractPayload(response)
      },
      { force, staleTime: 60 * 1000 },
    )
    return applyHistory(payload)
  } catch (error) {
    errorMessage.value = error.message || "포인트 내역을 불러오지 못했습니다."
    if (isInitialLoad) {
      items.value = []
    }
    alert(errorMessage.value)
    return null
  } finally {
    initialLoading.value = false
    refreshing.value = false
  }
}

const searchHistory = () => {
  page.value = 0
  loadHistory()
}

const movePage = (nextPage) => {
  if (nextPage < 0 || nextPage >= totalPages.value || nextPage === page.value) {
    return
  }
  page.value = nextPage
  loadHistory()
}

const formatPoint = (value, showPlus = false) => {
  const amount = Number(value || 0)
  const sign = showPlus && amount > 0 ? "+" : ""
  return `${sign}${amount.toLocaleString("ko-KR")}P`
}

const formatItemAmount = (amount) => {
  const value = Number(amount || 0)
  const sign = value > 0 ? "+" : ""
  return `${sign}${value.toLocaleString("ko-KR")}P`
}

const formatDate = (value) => {
  if (!value) return "날짜 정보 없음"
  return String(value).slice(0, 10).replaceAll("-", ".")
}

watch([activeType, activePeriod, sort], () => {
  page.value = 0
  loadHistory()
})

onMounted(loadHistory)
</script>

<template>
  <section class="point-history-page">
    <AppPageHeader
      class="page-heading"
      title="포인트 내역"
    >
      <template #leading>
        <RouterLink to="/point-shop" class="page-back-button" aria-label="포인트 샵으로 이동">
          <i class="bi bi-chevron-left" aria-hidden="true"></i>
        </RouterLink>
      </template>
    </AppPageHeader>

    <div class="summary-grid">
      <AppCard as="article" class="summary-card earned-card" padding="none">
        <span>총 적립 포인트</span>
        <strong>{{ formattedSummary.totalEarned }}</strong>
      </AppCard>
      <AppCard as="article" class="summary-card used-card" padding="none">
        <span>총 사용 포인트</span>
        <strong>{{ formattedSummary.totalUsed }}</strong>
      </AppCard>
      <AppCard as="article" class="summary-card balance-card" padding="none">
        <span>남은 포인트</span>
        <strong>{{ formattedSummary.balance }}</strong>
      </AppCard>
      <AppCard as="article" class="summary-card monthly-card" padding="none">
        <span>이번 달 변동</span>
        <strong>{{ formattedSummary.monthlyChange }}</strong>
      </AppCard>
    </div>

    <AppCard as="section" class="filter-panel" padding="none" aria-label="포인트 내역 필터">
      <div class="filter-tabs">
        <AppButton
          v-for="tab in tabs"
          :key="tab.value"
          class="filter-tab"
          size="sm"
          :variant="activeType === tab.value ? 'primary' : 'outline'"
          @click="activeType = tab.value"
        >
          {{ tab.label }}
        </AppButton>
      </div>

      <label class="filter-field">
        <span>정렬</span>
        <select v-model="sort" class="form-select">
          <option value="LATEST">최신순</option>
          <option value="OLDEST">오래된순</option>
        </select>
      </label>

      <div class="period-field">
        <span>기간</span>
        <div class="period-buttons">
          <AppButton
            v-for="period in periods"
            :key="period.value"
            class="period-button"
            size="sm"
            :variant="activePeriod === period.value ? 'primary' : 'outline'"
            @click="activePeriod = period.value"
          >
            {{ period.label }}
          </AppButton>
        </div>
      </div>

      <form class="search-field" @submit.prevent="searchHistory">
        <label for="point-history-keyword">내역 검색</label>
        <div class="input-group">
          <input
            id="point-history-keyword"
            v-model="keyword"
            type="search"
            class="form-control"
            placeholder="내역 검색"
          />
          <AppButton
            type="submit"
            class="search-button"
            variant="secondary"
            size="sm"
            aria-label="검색"
          >
            <i class="bi bi-search" aria-hidden="true"></i>
          </AppButton>
        </div>
      </form>
    </AppCard>

    <AppState
      v-if="initialLoading"
      class="state-message"
      type="loading"
      title="포인트 내역을 불러오는 중입니다."
      message="잠시만 기다려 주세요."
    />

    <AppAlert
      v-else-if="errorMessage && !hasLoadedHistory"
      class="state-message error-state"
      variant="danger"
    >
      <div class="point-state-content">
        <span>{{ errorMessage }}</span>
        <AppButton variant="outline" size="sm" @click="loadHistory({ force: true })">
          다시 시도
        </AppButton>
      </div>
    </AppAlert>

    <section v-else class="history-list-section">
      <AppAlert
        v-if="refreshing"
        class="state-message"
        variant="neutral"
        role="status"
        :show-icon="false"
        message="최신 포인트 내역을 확인하는 중입니다..."
      />
      <AppAlert v-if="errorMessage" class="state-message error-state" variant="danger">
        <div class="point-state-content">
          <span>{{ errorMessage }}</span>
          <AppButton variant="outline" size="sm" @click="loadHistory({ force: true })">
            다시 시도
          </AppButton>
        </div>
      </AppAlert>
      <AppCard v-if="items.length" as="div" class="history-list" padding="none">
        <article v-for="item in items" :key="item.id" class="history-item">
          <div class="history-icon" :class="item.type === 'EARN' ? 'earn-icon' : 'use-icon'">
            <i
              :class="item.type === 'EARN' ? 'bi bi-arrow-down-circle' : 'bi bi-arrow-up-circle'"
              aria-hidden="true"
            ></i>
          </div>
          <div class="history-main">
            <strong>{{ item.title }}</strong>
            <span>{{ item.description || "포인트 변동 내역" }}</span>
          </div>
          <span class="history-category">{{ item.category }}</span>
          <time class="history-date">{{ formatDate(item.createdAt) }}</time>
          <strong class="history-amount" :class="item.type === 'EARN' ? 'earn-text' : 'use-text'">
            {{ formatItemAmount(item.amount) }}
          </strong>
          <span class="history-status" :class="item.type === 'EARN' ? 'earn-status' : 'use-status'">
            {{ item.status }}
          </span>
        </article>
      </AppCard>

      <AppState
        v-else
        class="empty-state"
        type="empty"
        title="포인트 내역이 없습니다."
        message="조건을 바꾸거나 포인트를 사용해보세요."
      >
        <template #icon>
          <i class="bi bi-receipt" aria-hidden="true"></i>
        </template>
      </AppState>

      <nav v-if="totalPages > 1" class="pagination-wrap" aria-label="포인트 내역 페이지">
        <AppButton
          class="page-button"
          variant="secondary"
          size="sm"
          :disabled="page === 0"
          @click="movePage(page - 1)"
        >
          <i class="bi bi-chevron-left" aria-hidden="true"></i>
        </AppButton>
        <span>{{ page + 1 }} / {{ totalPages }}</span>
        <AppButton
          class="page-button"
          variant="secondary"
          size="sm"
          :disabled="page + 1 >= totalPages"
          @click="movePage(page + 1)"
        >
          <i class="bi bi-chevron-right" aria-hidden="true"></i>
        </AppButton>
      </nav>
    </section>
  </section>
</template>

<style scoped>
.page-back-button {
  display: inline-flex;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 12px;
  background: #f1efff;
  color: #6b64e8;
  text-decoration: none;
  transform: translateX(-8px);
  transition:
    background-color 160ms ease,
    color 160ms ease,
    transform 160ms ease;
}

.page-back-button:hover,
.page-back-button:focus-visible {
  background: #e8e5ff;
  color: #574fd2;
  transform: translateX(-8px) translateY(-1px);
}

.page-back-button:focus-visible {
  outline: 3px solid rgb(107 100 232 / 22%);
  outline-offset: 2px;
}

.page-back-button i {
  font-size: 16px;
  line-height: 1;
}

.point-history-page {
  width: 100%;
  color: #27304f;
}

.page-heading {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
}

.page-heading :deep(.app-page-header__description) {
  color: #8c95b0;
  font-size: 13px;
}

.point-state-content {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.summary-card {
  min-height: 102px;
  padding: 20px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(48 60 110 / 6%);
}

.summary-card span {
  color: #8f98b1;
  font-size: 12px;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  font-size: 25px;
}

.earned-card strong,
.earn-text {
  color: #18b982;
}

.used-card strong,
.use-text {
  color: #f2647f;
}

.balance-card strong {
  color: #6754e8;
}

.monthly-card strong {
  color: #e39b00;
}

.filter-panel {
  display: flex;
  align-items: end;
  gap: 16px;
  margin-bottom: 12px;
  padding: 16px 20px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(48 60 110 / 5%);
}

.filter-tabs,
.period-buttons {
  display: flex;
  gap: 4px;
}

.filter-tab,
.period-button {
  border: 1px solid #e8eaf3;
  background: #fff;
  color: #8b94ae;
  font-size: 12px;
}

.filter-tab {
  min-width: 58px;
  padding: 8px 14px;
  border-radius: 9px;
}

.filter-tab.active,
.period-button.active {
  border-color: #6754e8;
  background: #6754e8;
  color: #fff;
}

.filter-field,
.period-field,
.search-field {
  display: grid;
  gap: 5px;
  color: #9098b0;
  font-size: 11px;
}

.filter-field .form-select {
  min-width: 100px;
  padding-top: 7px;
  padding-bottom: 7px;
  border-color: #e6e8f1;
  color: #65708e;
  font-size: 12px;
}

.period-button {
  padding: 8px 11px;
  border-radius: 8px;
}

.search-field {
  min-width: 175px;
  margin-left: auto;
}

.search-field .form-control {
  border-color: #e6e8f1;
  font-size: 12px;
}

.search-button {
  border: 1px solid #e6e8f1;
  background: #fff;
  color: #6d7795;
}

.state-message,
.empty-state {
  display: grid;
  min-height: 180px;
  place-items: center;
  align-content: center;
  gap: 8px;
  border-radius: 18px;
  background: #fff;
  color: #8c95b0;
  font-size: 13px;
  text-align: center;
}

.error-state {
  display: flex;
  justify-content: space-between;
  min-height: auto;
  padding: 14px 18px;
  background: #fff1f1;
  color: #c85a67;
}

.retry-button {
  border: 1px solid #efb4bb;
  border-radius: 999px;
  color: #c85a67;
  font-size: 12px;
  font-weight: 700;
}

.history-list {
  overflow: hidden;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(48 60 110 / 5%);
}

.history-item {
  display: grid;
  grid-template-columns: 42px minmax(180px, 1fr) 90px 105px 100px 52px;
  align-items: center;
  gap: 14px;
  padding: 12px 18px;
  border-bottom: 1px solid #f0f1f6;
}

.history-item:last-child {
  border-bottom: 0;
}

.history-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 12px;
  font-size: 19px;
}

.earn-icon {
  background: #e8faf2;
  color: #19b987;
}

.use-icon {
  background: #fff0f3;
  color: #f2647f;
}

.history-main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.history-main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.history-main span,
.history-date {
  overflow: hidden;
  color: #9aa2bc;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-category,
.history-status {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  justify-self: start;
  padding: 5px 8px;
  border-radius: 999px;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-category {
  background: #f1efff;
  color: #7061d9;
}

.history-amount {
  font-size: 13px;
  text-align: right;
}

.history-status {
  justify-self: end;
}

.earn-status {
  background: #e8faf2;
  color: #19b987;
}

.use-status {
  background: #fff0f3;
  color: #f2647f;
}

.empty-state i {
  color: #8c7af1;
  font-size: 30px;
}

.empty-state strong {
  color: #4f5878;
}

.pagination-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 18px 0 4px;
  color: #7b84a1;
  font-size: 12px;
}

.page-button {
  width: 30px;
  height: 30px;
  border: 1px solid #e4e7f2;
  border-radius: 8px;
  background: #fff;
  color: #68719a;
}

.page-button:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

@media (max-width: 1000px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-panel {
    flex-wrap: wrap;
    align-items: center;
  }

  .search-field {
    width: 100%;
    margin-left: 0;
  }

  .history-item {
    grid-template-columns: 42px minmax(0, 1fr) 90px;
  }

  .history-date,
  .history-category,
  .history-status {
    display: none;
  }
}

@media (max-width: 600px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .page-heading {
    display: flex;
    gap: 12px !important;
  }
}
</style>
