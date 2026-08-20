<script setup>
import { computed } from "vue"
import BudgetGauge from "./BudgetGauge.vue"
import CategoryOverview from "./CategoryOverview.vue"
import InsufficientDataNotice from "./InsufficientDataNotice.vue"
import PositiveSignalList from "./PositiveSignalList.vue"
import RecurringPatternCard from "./RecurringPatternCard.vue"
import SpendingSignalList from "./SpendingSignalList.vue"
import SummaryMetrics from "./SummaryMetrics.vue"
import { formatWon } from "@/types/consumptionAnalysis"

const SPENDING_REPORT_IMAGES = [
  "03_스트레칭.png",
  "04_요가타임.png",
  "05_명상중.png",
  "06_아침인사.png",
  "07_하이파이브.png",
  "08_생각중.png",
  "09_메모중.png",
  "10_계획세우기.png",
  "11_체크리스트.png",
  "12_집중력MAX.png",
  "13_데스크정리.png",
]

const randomSpendingReportImage = () => {
  const index = Math.floor(Math.random() * SPENDING_REPORT_IMAGES.length)
  return `/images/spending/${SPENDING_REPORT_IMAGES[index]}`
}

const props = defineProps({ analysis: { type: Object, required: true } })
const spendingReportImage = randomSpendingReportImage()
const signals = computed(() => props.analysis.signals || {})
const focus = computed(() => props.analysis.focus || "OVERVIEW")
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
</script>

<template>
  <section class="consumption-analysis" aria-label="소비분석 결과">
    <InsufficientDataNotice
      v-if="!analysis.hasEnoughData"
      :info="analysis.insufficient"
    />
    <div class="d-flex flex-column gap-2" :class="{ 'mt-2': !analysis.hasEnoughData }">
      <div v-if="showSummary && analysis.summary" class="analysis-card">
        <SummaryMetrics :summary="analysis.summary" :period-label="analysis.period?.label" />
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
        <div v-for="item in signals.subscriptions" :key="item.merchant" class="d-flex justify-content-between small py-1">
          <span>{{ item.merchant }} · {{ item.count }}회 반복</span>
          <strong>{{ formatWon(item.amount) }}</strong>
        </div>
        <small class="text-secondary">정기결제로 확정하기 전에 사용자 확인이 필요해요.</small>
      </div>
      <div v-else-if="focus === 'SUBSCRIPTION'" class="analysis-card">
        <strong class="d-block mb-1">정기결제 후보</strong>
        <span class="text-secondary">현재 기준을 충족하는 정기결제 후보가 없어요.</span>
      </div>
    </div>
    <RouterLink class="report-link mt-2" :to="{ name: 'expenses' }">
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
.analysis-card { padding: 1rem; background: #fff; border: 1px solid #e9e6f3; border-radius: 1rem; box-shadow: 0 0.25rem 0.8rem rgba(57, 45, 110, 0.06); }
.analysis-card--good { background: #f3fbf6; border-color: #d7efe0; }
.analysis-card--caution { background: #fffbef; border-color: #f4e7bd; }
.report-link {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 0.75rem;
  padding: 0.8rem 0.9rem;
  color: #514879;
  background: #faf9ff;
  border: 1px solid #ded9fa;
  border-radius: 0.9rem;
  text-decoration: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}
.report-link:hover,
.report-link:focus-visible {
  color: #42369e;
  background: #f6f4ff;
  border-color: #bcb4f3;
  box-shadow: 0 0.3rem 0.9rem rgba(87, 73, 197, 0.1);
  transform: translateY(-1px);
}
.report-link:focus-visible { outline: 3px solid rgba(112, 98, 222, 0.2); outline-offset: 2px; }
.report-link__icon {
  display: inline-flex;
  width: 3.25rem;
  height: 3.25rem;
  flex: 0 0 3.25rem;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #eeebff;
  border-radius: 0.7rem;
}
.report-link__icon img { width: 100%; height: 100%; object-fit: contain; }
.report-link__copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 0.1rem; }
.report-link__copy strong { font-size: 0.88rem; }
.report-link__copy small { color: #7b849b; font-size: 0.75rem; }
.report-link__arrow { color: #8f86cf; font-size: 0.85rem; }
</style>
