<script setup>
import { computed } from "vue"
import {
  assetCategoryLabel,
  formatAssetAmount,
  formatAssetAmountRange,
  normalizeAssetAnalysis,
} from "@/types/assetAnalysis"

const props = defineProps({
  analysis: { type: Object, required: true },
})

const emptyAnalysis = {
  summary: {
    totalAssetsKrw: null,
    totalDebtKrw: null,
    netAssetsKrw: null,
  },
  cashflow: {
    monthlyNetIncomeKrw: null,
    monthlySavingKrw: null,
    monthlyExpenseKrw: null,
    monthlySurplusKrw: null,
    annualSavingKrw: null,
    savingRatePercent: null,
  },
  composition: [],
  dataQualityNotes: [],
}

const normalizedAnalysis = computed(() =>
  normalizeAssetAnalysis(props.analysis) || emptyAnalysis,
)
const summary = computed(() => normalizedAnalysis.value.summary)
const cashflow = computed(() => normalizedAnalysis.value.cashflow)
const composition = computed(() => [...normalizedAnalysis.value.composition].sort(
  (left, right) => Number(right.sharePercent || 0) - Number(left.sharePercent || 0),
))
const notes = computed(() => normalizedAnalysis.value.dataQualityNotes)

const hasSummary = computed(() =>
  Object.values(summary.value).some((value) => value !== null),
)
const hasCashflow = computed(() =>
  Object.values(cashflow.value).some((value) => value !== null),
)
const hasComposition = computed(() => composition.value.length > 0)
const hasNotes = computed(() => notes.value.length > 0)
const hasCards = computed(() => hasSummary.value || hasCashflow.value || hasComposition.value)

const formatRate = (value) => {
  if (value === null || value === undefined) return "-"
  return `${Number(value).toLocaleString("ko-KR", {
    maximumFractionDigits: 1,
  })}%`
}

const progressWidth = (value) =>
  `${Math.min(100, Math.max(0, Number(value) || 0))}%`

const surplusClass = computed(() =>
  Number(cashflow.value.monthlySurplusKrw) >= 0
    ? "text-success"
    : "text-danger",
)
</script>

<template>
  <section class="asset-analysis" aria-label="자산분석 결과">
    <div class="asset-analysis__intro">
      <span class="asset-analysis__eyebrow">
        <i class="bi bi-pie-chart-fill me-1" aria-hidden="true"></i>
        AI 자산분석
      </span>
      <strong>현재 자산 상태를 한눈에 확인해보세요</strong>
    </div>

    <div v-if="hasSummary" class="asset-analysis-card">
      <div class="asset-analysis-card__heading">
        <span class="asset-analysis-card__eyebrow">핵심 요약</span>
        <h3 class="asset-analysis-card__title">자산 현황</h3>
      </div>
      <div class="row g-2">
        <div v-if="summary.totalAssetsKrw !== null" class="col-12 col-sm-4">
          <div class="asset-metric">
            <small>총자산</small>
            <strong>{{ formatAssetAmount(summary.totalAssetsKrw) }}</strong>
          </div>
        </div>
        <div v-if="summary.totalDebtKrw !== null" class="col-12 col-sm-4">
          <div class="asset-metric">
            <small>총부채</small>
            <strong>{{ formatAssetAmount(summary.totalDebtKrw) }}</strong>
          </div>
        </div>
        <div v-if="summary.netAssetsKrw !== null" class="col-12 col-sm-4">
          <div class="asset-metric asset-metric--accent">
            <small>순자산</small>
            <strong>{{ formatAssetAmount(summary.netAssetsKrw) }}</strong>
          </div>
        </div>
      </div>
    </div>

    <div v-if="hasCashflow" class="asset-analysis-card">
      <div class="asset-analysis-card__heading">
        <span class="asset-analysis-card__eyebrow">월간 현금흐름</span>
        <h3 class="asset-analysis-card__title">소득과 저축</h3>
      </div>
      <div class="row g-2">
        <div v-if="cashflow.monthlyNetIncomeKrw !== null" class="col-6 col-md-3">
          <div class="asset-metric asset-metric--compact">
            <small>월소득</small>
            <strong>{{ formatAssetAmount(cashflow.monthlyNetIncomeKrw) }}</strong>
          </div>
        </div>
        <div v-if="cashflow.monthlyExpenseKrw !== null" class="col-6 col-md-3">
          <div class="asset-metric asset-metric--compact">
            <small>월지출</small>
            <strong>{{ formatAssetAmount(cashflow.monthlyExpenseKrw) }}</strong>
          </div>
        </div>
        <div v-if="cashflow.monthlySavingKrw !== null" class="col-6 col-md-3">
          <div class="asset-metric asset-metric--compact">
            <small>월저축</small>
            <strong>{{ formatAssetAmount(cashflow.monthlySavingKrw) }}</strong>
          </div>
        </div>
        <div v-if="cashflow.monthlySurplusKrw !== null" class="col-6 col-md-3">
          <div class="asset-metric asset-metric--compact">
            <small>월 잉여금</small>
            <strong :class="surplusClass">
              {{ formatAssetAmount(cashflow.monthlySurplusKrw) }}
            </strong>
          </div>
        </div>
      </div>
      <div v-if="cashflow.savingRatePercent !== null" class="asset-rate mt-3">
        <div class="d-flex justify-content-between small mb-1">
          <span class="text-secondary">저축률</span>
          <strong>{{ formatRate(cashflow.savingRatePercent) }}</strong>
        </div>
        <div class="progress" role="progressbar" aria-label="저축률">
          <div
            class="progress-bar bg-success"
            :style="{ width: progressWidth(cashflow.savingRatePercent) }"
          ></div>
        </div>
      </div>
    </div>

    <div v-if="hasComposition" class="asset-analysis-card">
      <div class="asset-analysis-card__heading">
        <span class="asset-analysis-card__eyebrow">자산 구성</span>
        <h3 class="asset-analysis-card__title">보유 자산별 비중</h3>
      </div>
      <div class="d-flex flex-column gap-2">
        <div
          v-for="(item, index) in composition"
          :key="`${item.category || 'asset'}-${item.name || index}-${index}`"
          class="asset-composition-item"
        >
          <div class="d-flex align-items-start justify-content-between gap-3">
            <div class="min-w-0">
              <strong class="d-block text-truncate">{{ item.name || "자산 항목" }}</strong>
              <small class="text-secondary">
                {{ assetCategoryLabel(item.category) }}
                <span v-if="item.estimated" class="badge text-bg-warning-subtle text-warning-emphasis ms-1">
                  추정
                </span>
              </small>
            </div>
            <div class="text-end flex-shrink-0">
              <strong>{{ formatAssetAmountRange(item) }}</strong>
              <small v-if="item.sharePercent !== null" class="d-block text-secondary">
                {{ formatRate(item.sharePercent) }}
              </small>
            </div>
          </div>
          <div v-if="item.sharePercent !== null" class="progress asset-composition-progress mt-2">
            <div
              class="progress-bar"
              :style="{ width: progressWidth(item.sharePercent) }"
            ></div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="hasNotes" class="alert alert-warning-subtle border rounded-4 mb-0" role="note">
      <div class="d-flex gap-2">
        <i class="bi bi-info-circle fs-5" aria-hidden="true"></i>
        <div>
          <strong class="d-block mb-1">분석 참고</strong>
          <ul class="small mb-0 ps-3">
            <li v-for="note in notes" :key="note">{{ note }}</li>
          </ul>
        </div>
      </div>
    </div>

    <div v-if="!hasCards && !hasNotes" class="alert alert-light border rounded-4 mb-0" role="status">
      <i class="bi bi-info-circle me-2" aria-hidden="true"></i>
      표시할 자산분석 데이터가 충분하지 않습니다.
    </div>
  </section>
</template>

<style scoped>
.asset-analysis {
  display: flex;
  width: min(100%, 680px);
  flex-direction: column;
  gap: 0.65rem;
  margin-bottom: 0.75rem;
}

.asset-analysis__intro {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.1rem 0.2rem;
  color: #3f4660;
}

.asset-analysis__eyebrow,
.asset-analysis-card__eyebrow {
  color: #7062de;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.03em;
}

.asset-analysis-card {
  padding: 1rem;
  background: #fff;
  border: 1px solid #e9e6f3;
  border-radius: 1rem;
  box-shadow: 0 0.25rem 0.8rem rgba(57, 45, 110, 0.06);
}

.asset-analysis-card__heading {
  margin-bottom: 0.85rem;
}

.asset-analysis-card__title {
  margin: 0.15rem 0 0;
  color: #2f354d;
  font-size: 1rem;
  font-weight: 700;
}

.asset-metric {
  display: flex;
  height: 100%;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.75rem;
  background: #f8f7fc;
  border-radius: 0.75rem;
}

.asset-metric small {
  color: #7b849b;
  font-size: 0.75rem;
}

.asset-metric strong {
  color: #30364d;
  font-size: 1.05rem;
}

.asset-metric--accent {
  background: #f0eefe;
}

.asset-metric--accent strong {
  color: #5749c5;
}

.asset-metric--compact strong {
  font-size: 0.9rem;
}

.asset-rate .progress,
.asset-composition-progress {
  height: 0.45rem;
  background: #eceaf5;
}

.asset-composition-progress .progress-bar {
  background: #7062de;
}

.asset-composition-item {
  padding: 0.7rem 0;
  border-bottom: 1px solid #ece9f3;
}

.asset-composition-item:first-child {
  padding-top: 0;
}

.asset-composition-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

@media (max-width: 640px) {
  .asset-analysis-card {
    padding: 0.85rem;
  }

  .asset-metric strong {
    font-size: 0.95rem;
  }
}
</style>
