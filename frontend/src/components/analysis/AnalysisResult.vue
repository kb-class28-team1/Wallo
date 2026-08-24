<script setup>
import { computed, ref } from "vue"
import BudgetGauge from "./BudgetGauge.vue"
import CategoryOverview from "./CategoryOverview.vue"
import InsufficientDataNotice from "./InsufficientDataNotice.vue"
import PositiveSignalList from "./PositiveSignalList.vue"
import RecurringPatternCard from "./RecurringPatternCard.vue"
import SpendingSignalList from "./SpendingSignalList.vue"
import SummaryMetrics from "./SummaryMetrics.vue"
import { categoryLabel, comparisonPeriodLabel, formatWon } from "@/types/consumptionAnalysis"

const SPENDING_REPORT_IMAGES = [
  "03_스트레칭.webp",
  "04_요가타임.webp",
  "05_명상중.webp",
  "06_아침인사.webp",
  "07_하이파이브.webp",
  "08_생각중.webp",
  "09_메모중.webp",
  "10_계획세우기.webp",
  "11_체크리스트.webp",
  "12_집중력MAX.webp",
  "13_데스크정리.webp",
]

const randomSpendingReportImage = () => {
  const index = Math.floor(Math.random() * SPENDING_REPORT_IMAGES.length)
  return `/images/spending/${SPENDING_REPORT_IMAGES[index]}`
}

const props = defineProps({
  analysis: { type: Object, required: true },
  compact: { type: Boolean, default: false },
})
const expanded = ref(false)
const spendingReportImage = randomSpendingReportImage()
const signals = computed(() => props.analysis.signals || {})
const focus = computed(() => props.analysis.focus || "OVERVIEW")
const comparisonLabel = computed(() => comparisonPeriodLabel(props.analysis.period))
const has = (items) => Array.isArray(items) && items.length > 0
const allows = (...values) => focus.value === "OVERVIEW" || values.includes(focus.value)
const showCaution = computed(() =>
  allows("CHANGE", "SIGNALS") && (
    has(signals.value.categorySpikes) || has(signals.value.newSpendings) ||
    has(signals.value.oneTimeLarge)
  ),
)
const showGood = computed(() =>
  allows("IMPROVEMENT") && (
    has(signals.value.positives) || has(signals.value.streaks)
  ),
)
const showSummary = computed(() => allows("CHANGE", "BUDGET", "IMPROVEMENT"))
const showBudget = computed(() => allows("BUDGET"))
const showCategories = computed(() => allows("CATEGORY"))
const showPatterns = computed(() => allows("PATTERN"))
const showSubscriptions = computed(() => allows("SUBSCRIPTION"))
const topCategories = computed(() => [...(signals.value.categoryOverview || [])]
  .sort((left, right) => Number(right.current || 0) - Number(left.current || 0))
  .slice(0, 3))
const deltaClass = computed(() => Number(props.analysis.summary?.deltaAmount) <= 0
  ? "text-success"
  : "text-danger")
</script>

<template>
  <section class="consumption-analysis" aria-label="소비분석 결과">
    <InsufficientDataNotice
      v-if="!analysis.hasEnoughData"
      :info="analysis.insufficient"
    />
    <div v-if="compact && analysis.summary" class="consumption-overview">
      <div class="consumption-overview__hero">
        <span>{{ analysis.period?.label || "현재 기간" }} 총지출</span>
        <strong>{{ formatWon(analysis.summary.currentTotal) }}</strong>
        <small>
          {{ comparisonLabel }}보다
          <span :class="['consumption-overview__delta-amount', deltaClass]">
            {{ formatWon(Math.abs(analysis.summary.deltaAmount || 0)) }}
          </span>
          {{ Number(analysis.summary.deltaAmount) <= 0 ? "줄었어요" : "늘었어요" }}
        </small>
      </div>
      <div v-if="topCategories.length" class="consumption-top-categories">
        <div class="consumption-overview__label">지출이 큰 카테고리</div>
        <div v-for="item in topCategories" :key="item.categoryCode" class="consumption-category-row">
          <span>{{ categoryLabel(item.categoryCode) }}</span>
          <strong>{{ formatWon(item.current) }}</strong>
        </div>
      </div>
      <div v-if="showCaution" class="consumption-highlight consumption-highlight--warning">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <div><span>주의해서 볼 소비가 있어요</span><small>상세 분석에서 증가·신규 지출을 확인하세요.</small></div>
      </div>
      <div v-else-if="showGood" class="consumption-highlight consumption-highlight--good">
        <i class="bi bi-check2-circle" aria-hidden="true"></i>
        <div><span>좋은 소비 변화가 보여요</span><small>이 흐름을 다음 기간에도 유지해보세요.</small></div>
      </div>
    </div>
    <div v-if="!compact || expanded" class="analysis-detail-list" :class="{ 'mt-2': !analysis.hasEnoughData }">
      <div v-if="showSummary && analysis.summary && !compact" class="analysis-card">
        <SummaryMetrics
          :summary="analysis.summary"
          :period-label="analysis.period?.label"
          :comparison-label="comparisonLabel"
        />
      </div>
      <div v-if="showBudget && signals.budget?.hasBudget" class="analysis-card">
        <BudgetGauge :budget="signals.budget" />
      </div>
      <div
        v-else-if="focus === 'BUDGET'"
        class="analysis-card"
      >
        <strong class="d-block mb-1">예산 상태</strong>
        <span class="text-secondary">해당 분석 월에 설정된 예산이 없어요.</span>
      </div>
      <div v-if="showCategories && has(signals.categoryOverview)" class="analysis-card">
        <strong class="d-block mb-2">카테고리별 소비 내역</strong>
        <CategoryOverview
          :categories="signals.categoryOverview"
          :repeating-categories="signals.repeatingCategories"
          :comparison-label="comparisonLabel"
        />
      </div>
      <div v-if="showGood" class="analysis-card analysis-card--good">
        <strong class="d-block mb-2 text-success">잘한 점</strong>
        <PositiveSignalList :positives="signals.positives" :streaks="signals.streaks" />
      </div>
      <div v-if="showCaution" class="analysis-card analysis-card--caution">
        <strong class="d-block mb-2 text-warning-emphasis">주의해서 볼 점</strong>
        <SpendingSignalList
          :spikes="signals.categorySpikes"
          :new-spendings="signals.newSpendings"
          :one-time-large="signals.oneTimeLarge"
        />
      </div>
      <div v-if="showPatterns && has(signals.recurringPatterns)" class="analysis-card">
        <strong class="d-block mb-2">반복 소비 패턴</strong>
        <div class="d-flex flex-column gap-2">
          <RecurringPatternCard
            v-for="(pattern, index) in signals.recurringPatterns"
            :key="`${pattern.type}-${index}`"
            :pattern="pattern"
          />
        </div>
      </div>
      <div v-else-if="focus === 'PATTERN'" class="analysis-card">
        <strong class="d-block mb-1">반복 소비 패턴</strong>
        <span class="text-secondary">현재 기준을 충족하는 반복 소비 패턴이 없어요.</span>
      </div>
      <div v-if="showSubscriptions && has(signals.subscriptions)" class="analysis-card">
        <strong class="d-block mb-2"><i class="bi bi-arrow-repeat me-2"></i>정기결제 후보</strong>
        <div
          v-for="item in signals.subscriptions"
          :key="item.merchant"
          class="subscription-row small py-1"
        >
          <span class="subscription-row__merchant">{{ item.merchant }}</span>
          <span class="subscription-row__count">{{ item.count }}회 반복</span>
          <strong class="subscription-row__amount">{{ formatWon(item.amount) }}</strong>
        </div>
      </div>
      <div v-else-if="focus === 'SUBSCRIPTION'" class="analysis-card">
        <strong class="d-block mb-1">정기결제 후보</strong>
        <span class="text-secondary">현재 기준을 충족하는 정기결제 후보가 없어요.</span>
      </div>
    </div>
    <button
      v-if="compact && analysis.summary"
      type="button"
          class="analysis-detail-toggle mt-2 pressable"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      {{ expanded ? "상세 내용 접기" : "전체 분석 자세히 보기" }}
      <i :class="expanded ? 'bi bi-chevron-up' : 'bi bi-chevron-down'" aria-hidden="true"></i>
    </button>
    <RouterLink class="report-link mt-2 pressable" :to="{ name: 'expenses' }">
      <span class="report-link__icon">
        <img :src="spendingReportImage" alt="" />
      </span>
      <span class="report-link__copy">
        <strong>월별 소비 리포트</strong>
        <small>소비 내역을 더 자세히 확인해 보세요</small>
      </span>
      <i class="bi bi-chevron-right report-link__arrow" aria-hidden="true"></i>
    </RouterLink>
  </section>
</template>

<style scoped>
.consumption-analysis { display: flex; width: min(100%, 680px); flex-direction: column; margin-bottom: 0.75rem; }
.consumption-overview { display: grid; gap: 0.75rem; }
.consumption-overview__hero { padding: 1.4rem; background: linear-gradient(135deg, #eff7ff, #f8fbff); border: 1px solid var(--wallo-color-border); border-radius: 1rem; }
.consumption-overview__hero > span, .consumption-overview__hero > small { display: block; }
.consumption-overview__hero > span, .consumption-overview__label { color: var(--wallo-color-text-muted); font-size: 0.9rem; }
.consumption-overview__hero > small { color: var(--wallo-color-text); }
.consumption-overview__hero strong { display: block; margin: 0.35rem 0; color: var(--wallo-color-text); font-size: clamp(1.6rem, 4vw, 2.2rem); }
.consumption-top-categories { padding: 1rem; background: var(--wallo-color-surface-soft); border: 1px solid var(--wallo-color-border-soft); border-radius: 0.9rem; }
.consumption-overview__label { margin-bottom: 0.45rem; font-weight: 700; }
.consumption-category-row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 0.55rem 0; border-bottom: 1px solid var(--wallo-color-border-soft); }
.consumption-category-row:last-child { border-bottom: 0; }
.consumption-highlight { display: flex; align-items: flex-start; gap: 0.7rem; padding: 0.9rem 1rem; border-radius: 0.85rem; }
.consumption-highlight span, .consumption-highlight small { display: block; }
.consumption-highlight small { margin-top: 0.2rem; }
.consumption-highlight--warning { color: #805d18; background: #fff9e9; border: 1px solid #f4e7bd; }
.consumption-highlight--good { color: #176b4d; background: #f2faf5; }
.analysis-detail-list { display: flex; flex-direction: column; gap: 0.75rem; }
.consumption-overview + .analysis-detail-list { margin-top: 0.75rem; }
.analysis-detail-toggle { display: flex; width: 100%; align-items: center; justify-content: center; gap: 0.45rem; padding: 0.75rem; color: var(--wallo-color-primary-hover); background: transparent; border: 1px solid var(--wallo-color-border); border-radius: 0.8rem; font: inherit; font-size: 0.85rem; font-weight: 700; }
.analysis-detail-toggle:hover { background: var(--wallo-color-info-bg); }
.analysis-detail-toggle:focus-visible { outline: 0; box-shadow: var(--wallo-focus-ring); }
.analysis-card { padding: 1rem; background: var(--wallo-color-surface); border: 1px solid var(--wallo-color-border); border-radius: 1rem; }
.analysis-card--good { background: #f3fbf6; border-color: #d7efe0; }
.analysis-card--caution { background: #fffbef; border-color: #f4e7bd; }
.subscription-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 5rem 6.5rem;
  align-items: center;
  column-gap: 0.75rem;
}
.subscription-row__merchant { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.subscription-row__count { color: var(--wallo-color-text-muted); white-space: nowrap; }
.subscription-row__amount { text-align: right; white-space: nowrap; }
.report-link {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 0.75rem;
  padding: 0.8rem 0.9rem;
  color: var(--wallo-color-text);
  background: var(--wallo-color-surface-soft);
  border: 1px solid var(--wallo-color-border);
  border-radius: 0.9rem;
  text-decoration: none;
  transition: border-color 0.15s ease, transform 0.15s ease;
}
.report-link:hover,
.report-link:focus-visible {
  color: var(--wallo-color-primary-hover);
  background: var(--wallo-color-surface-soft);
  border-color: var(--wallo-color-primary);
  transform: translateY(-1px);
}
.report-link:active {
  transform: scale(0.98);
}
.report-link:focus-visible { outline: 0; box-shadow: var(--wallo-focus-ring); }
.report-link__icon {
  display: inline-flex;
  width: 3.25rem;
  height: 3.25rem;
  flex: 0 0 3.25rem;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--wallo-color-surface-soft);
  border-radius: 0.7rem;
}
.report-link__icon img { width: 100%; height: 100%; object-fit: contain; }
.report-link__copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 0.1rem; }
.report-link__copy strong { font-size: 0.88rem; }
.report-link__copy small { color: var(--wallo-color-text-muted); font-size: 0.75rem; }
.report-link__arrow { color: var(--wallo-color-text-subtle); font-size: 0.85rem; }
</style>
