<script setup>
import { computed, onMounted, ref } from "vue"
import { storeToRefs } from "pinia"
import { ANNUAL_SALARY_LOOKUP_STATUS, useReportStore } from "@/stores/assetReportStore.js"
import { formatNumber, formatWon } from "@/utils/formatters"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppState from "@/components/ui/AppState.vue"

const SEVEN_MILLION_WON = 70_000_000
const TWELVE_MILLION_WON = 120_000_000
const BASIC_DEDUCTION_LIMIT_UNDER_SEVEN_MILLION = 3_000_000
const BASIC_DEDUCTION_LIMIT_UNDER_TWELVE_MILLION = 2_500_000
const BASIC_DEDUCTION_LIMIT_OVER_TWELVE_MILLION = 2_000_000
const ADDITIONAL_DEDUCTION_LIMIT_UNDER_SEVEN_MILLION = 6_000_000

const reportStore = useReportStore()
const props = defineProps({
  forceRefresh: {
    type: Boolean,
    default: false,
  },
})
const {
  taxSettlement,
  initialTaxSettlementLoading,
  refreshingTaxSettlement,
  isTaxSettlementLoading,
  taxSettlementError,
  annualSalaryLookupStatus,
  isAnnualSalarySaving,
  annualSalaryError,
} = storeToRefs(reportStore)
const annualSalaryInput = ref("")
const manualSalaryError = ref("")

const isInitialLoading = computed(
  () =>
    initialTaxSettlementLoading?.value ??
    Boolean(isTaxSettlementLoading?.value && !taxSettlement.value),
)
const isRefreshing = computed(() => refreshingTaxSettlement?.value ?? false)

const annualSalary = computed(() => Number(taxSettlement.value?.annualSalary ?? 0))

const spentAmount = computed(() => Math.max(Number(taxSettlement.value?.cardSpentYtd ?? 0), 0))
const thresholdAmount = computed(() =>
  Math.max(Number(taxSettlement.value?.creditCardThreshold ?? 0), 0),
)
const remainingAmount = computed(() => Math.max(thresholdAmount.value - spentAmount.value, 0))
const isThresholdReached = computed(
  () => thresholdAmount.value > 0 && spentAmount.value >= thresholdAmount.value,
)
const salaryBracket = computed(() => {
  if (annualSalary.value <= SEVEN_MILLION_WON) return "under-seven-million"
  if (annualSalary.value <= TWELVE_MILLION_WON) return "under-twelve-million"
  return "over-twelve-million"
})
const basicDeductionLimit = computed(() => {
  if (salaryBracket.value === "under-seven-million") {
    return BASIC_DEDUCTION_LIMIT_UNDER_SEVEN_MILLION
  }

  if (salaryBracket.value === "under-twelve-million") {
    return BASIC_DEDUCTION_LIMIT_UNDER_TWELVE_MILLION
  }

  return BASIC_DEDUCTION_LIMIT_OVER_TWELVE_MILLION
})
const maximumDeductionLimit = computed(() =>
  salaryBracket.value === "under-seven-million"
    ? ADDITIONAL_DEDUCTION_LIMIT_UNDER_SEVEN_MILLION
    : basicDeductionLimit.value,
)
const formatTenThousandWon = (amount) => `${formatNumber(amount / 10_000)}만 원`
const progressLabel = computed(() => (isThresholdReached.value ? "달성" : `${progressRate.value}%`))
const strategyTitle = computed(() =>
  isThresholdReached.value ? "체크카드·현금영수증 절세 구간" : "신용카드 혜택 구간",
)
const strategyMessage = computed(() =>
  isThresholdReached.value
    ? "이제부터 체크카드·현금영수증 비중을 늘려보세요."
    : "공제는 아직 시작되지 않아요. 혜택 좋은 신용카드를 우선 사용하세요.",
)

const isAnnualSalaryUnavailable = computed(
  () => annualSalaryLookupStatus.value === ANNUAL_SALARY_LOOKUP_STATUS.UNAVAILABLE,
)

const hasTaxSettlementContent = computed(
  () => Boolean(taxSettlement.value) && !isAnnualSalaryUnavailable.value,
)

const isTaxSettlementError = computed(
  () =>
    !isAnnualSalaryUnavailable.value &&
    (annualSalaryLookupStatus.value === ANNUAL_SALARY_LOOKUP_STATUS.ERROR ||
      Boolean(taxSettlementError.value) ||
      Boolean(annualSalaryError.value)),
)

const taxSettlementErrorMessage = computed(
  () =>
    taxSettlementError.value ||
    annualSalaryError.value ||
    "소득공제 달성률을 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
)

const achievementRate = computed(() => {
  if (
    !Number.isFinite(spentAmount.value) ||
    !Number.isFinite(thresholdAmount.value) ||
    thresholdAmount.value <= 0
  ) {
    return 0
  }

  return Math.round((spentAmount.value / thresholdAmount.value) * 100)
})

const progressRate = computed(() => Math.min(Math.max(achievementRate.value, 0), 100))

const loadTaxSettlement = async ({ force = false } = {}) => {
  if (
    !force &&
    (annualSalaryLookupStatus.value === ANNUAL_SALARY_LOOKUP_STATUS.UNAVAILABLE ||
      annualSalaryLookupStatus.value === ANNUAL_SALARY_LOOKUP_STATUS.ERROR)
  ) {
    return
  }

  try {
    await reportStore.fetchTaxSettlement(undefined, { force })
  } catch {
    // 조회 오류와 재시도 상태는 Pinia에서 관리합니다.
  }
}

const formatManualSalaryInput = () => {
  const numericValue = String(annualSalaryInput.value).replace(/[^0-9]/g, "")

  annualSalaryInput.value = numericValue ? formatNumber(numericValue) : ""
  manualSalaryError.value = ""
  reportStore.annualSalaryError = null
}

const submitManualSalary = async () => {
  const salary = Number(String(annualSalaryInput.value).replaceAll(",", ""))

  if (!Number.isFinite(salary) || salary <= 0) {
    manualSalaryError.value = "연봉은 0원보다 큰 금액으로 입력해 주세요."
    return
  }

  try {
    await reportStore.saveAnnualSalary(salary)
    annualSalaryInput.value = ""
    manualSalaryError.value = ""
  } catch {
    manualSalaryError.value = annualSalaryError.value
  }
}

const retryTaxSettlement = async () => {
  try {
    await reportStore.fetchTaxSettlement(undefined, { force: true })
  } catch {
    // 재시도 결과는 Pinia 상태를 통해 카드에 표시합니다.
  }
}

onMounted(() => loadTaxSettlement({ force: props.forceRefresh }))
</script>

<template>
  <AppCard class="tax-deduction-card h-100" padding="none">
    <div class="tax-deduction-body">
      <div class="tax-deduction-card-header">
        <h2 class="h5 fw-bold mb-0">공제 문턱 달성률</h2>

        <div class="tax-guide">
          <AppButton
            class="tax-guide-toggle"
            variant="ghost"
            size="sm"
            aria-haspopup="true"
            aria-describedby="tax-guide-panel"
          >
            <span>연봉별 기준 보기</span>
            <template #trailing>
              <i class="bi bi-info-circle" aria-hidden="true"></i>
            </template>
          </AppButton>

          <div id="tax-guide-panel" class="tax-guide-panel" role="tooltip">
            <div class="tax-guide-section">
              <strong>공제 문턱</strong>
              <p>총급여액의 25%를 초과해서 사용해야 공제가 시작됩니다.</p>
            </div>

            <div class="tax-guide-section">
              <strong>최대 공제한도</strong>
              <ul class="tax-guide-list">
                <li :class="{ 'is-current': salaryBracket === 'under-seven-million' }">
                  연봉 7,000만 원 이하: 기본 {{ formatTenThousandWon(3_000_000) }} · 최대
                  {{ formatTenThousandWon(6_000_000) }}
                </li>
                <li :class="{ 'is-current': salaryBracket === 'under-twelve-million' }">
                  연봉 7,000만 원 초과 ~ 1억 2,000만 원 이하: 기본
                  {{ formatTenThousandWon(2_500_000) }}
                </li>
                <li :class="{ 'is-current': salaryBracket === 'over-twelve-million' }">
                  연봉 1억 2,000만 원 초과: 기본 {{ formatTenThousandWon(2_000_000) }}
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>

      <AppState
        v-if="isInitialLoading"
        class="tax-deduction-state"
        type="loading"
        title="소득공제 달성률을 불러오는 중입니다."
        message="카드 사용 내역을 계산하고 있습니다."
        compact
      />

      <div v-else-if="hasTaxSettlementContent" class="tax-deduction-content">
        <div v-if="isRefreshing" class="small text-secondary mb-3" role="status">
          <span
            class="spinner-border spinner-border-sm text-primary me-2"
            aria-hidden="true"
          ></span>
          소득공제 정보를 최신 상태로 갱신하고 있습니다.
        </div>

        <AppAlert
          v-if="isTaxSettlementError"
          class="tax-sync-alert"
          variant="warning"
          :show-icon="false"
        >
          <span>최신 소득공제 정보를 갱신하지 못했습니다. 기존 정보를 표시하고 있습니다.</span>
          <AppButton variant="outline" size="sm" @click="retryTaxSettlement">다시 시도</AppButton>
        </AppAlert>

        <div class="tax-deduction-summary mb-3">
          <div class="tax-deduction-usage">
            <span>현재 사용액</span>
            <strong>{{ formatWon(spentAmount) }}</strong>
            <span class="tax-deduction-usage-divider">/</span>
            <span>공제 문턱</span>
            <strong>{{ formatWon(thresholdAmount) }}</strong>
          </div>
          <strong class="tax-deduction-rate">{{ progressLabel }}</strong>
        </div>

        <div
          class="progress tax-deduction-progress"
          role="progressbar"
          aria-label="공제 문턱 달성률"
          :aria-valuenow="progressRate"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <div class="progress-bar" :style="{ width: `${progressRate}%` }"></div>
        </div>

        <p class="tax-deduction-message mb-0">
          <template v-if="isThresholdReached">공제 문턱을 달성했어요.</template>
          <template v-else>
            공제가 시작되기까지
            <strong class="tax-deduction-remaining">{{ formatWon(remainingAmount) }}</strong>
            남았어요.
          </template>
        </p>

        <div class="tax-strategy" :class="{ 'tax-strategy--reached': isThresholdReached }">
          <div class="tax-strategy-heading">
            <strong>{{ strategyTitle }}</strong>
          </div>
          <p class="mb-0">{{ strategyMessage }}</p>
        </div>
      </div>

      <div v-else-if="isAnnualSalaryUnavailable" class="tax-deduction-state manual-salary-state">
        <p class="fw-semibold mb-1">세전 연봉을 자동으로 조회하지 못했습니다.</p>
        <p class="small text-secondary mb-3">
          연봉을 직접 입력하면 소득공제 달성률을 계산할 수 있습니다.
        </p>

        <form class="manual-salary-form" @submit.prevent="submitManualSalary">
          <label for="manualAnnualSalary" class="visually-hidden">세전 연봉</label>
          <div class="input-group">
            <input
              id="manualAnnualSalary"
              v-model="annualSalaryInput"
              type="text"
              class="form-control"
              inputmode="numeric"
              autocomplete="off"
              placeholder="예: 50,000,000"
              required
              :disabled="isAnnualSalarySaving"
              @input="formatManualSalaryInput"
            />
            <span class="input-group-text">원</span>
          </div>
          <p
            v-if="manualSalaryError || annualSalaryError"
            class="small text-danger mb-2"
            role="alert"
          >
            {{ manualSalaryError || annualSalaryError }}
          </p>
          <AppButton
            type="submit"
            class="w-100 mt-4"
            variant="primary"
            :disabled="isAnnualSalarySaving"
            :loading="isAnnualSalarySaving"
          >
            수동 연봉 입력
          </AppButton>
        </form>
      </div>

      <AppState
        v-else-if="isTaxSettlementError"
        class="tax-deduction-state"
        type="error"
        title="소득공제 달성률을 불러오지 못했습니다."
        :message="taxSettlementErrorMessage"
        compact
      >
        <template #actions>
          <AppButton
            class="btn-outline-danger"
            variant="outline"
            size="sm"
            @click="retryTaxSettlement"
          >
            다시 시도
          </AppButton>
        </template>
      </AppState>

      <AppState
        v-else
        class="tax-deduction-state"
        type="empty"
        title="세전 연봉 정보가 없습니다."
        message="금융기관 연결을 완료하면 CODEF에서 세전 연봉을 자동 조회합니다."
        compact
      />
    </div>
  </AppCard>
</template>

<style scoped>
.tax-deduction-card {
  min-height: 310px;
  border-radius: var(--wallo-radius-xl);
}

.tax-deduction-body {
  display: flex;
  min-height: 310px;
  flex-direction: column;
  padding: var(--wallo-space-6);
}

.tax-sync-alert {
  margin-bottom: var(--wallo-space-4);
}

.tax-deduction-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding-top: 30px;
}

.tax-deduction-card-header {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.tax-deduction-card-header h2 {
  min-width: 0;
}

.tax-deduction-state {
  display: flex;
  min-height: 220px;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.manual-salary-form {
  width: 100%;
  max-width: 360px;
}

.tax-deduction-rate {
  flex-shrink: 0;
  color: var(--wallo-color-primary);
  font-size: 1.35rem;
}

.tax-deduction-summary {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.tax-deduction-progress {
  height: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--wallo-color-info-bg);
}

.tax-deduction-progress .progress-bar {
  border-radius: inherit;
  background: var(--wallo-color-primary);
}

.tax-deduction-usage {
  display: flex;
  min-width: 0;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.35rem;
  color: var(--wallo-color-text-muted);
  font-size: 0.9rem;
}

.tax-deduction-usage strong {
  color: var(--wallo-color-text);
  font-size: 0.9rem;
}

.tax-deduction-usage-divider {
  margin: 0 0.1rem;
  color: var(--wallo-color-border);
}

.tax-deduction-message {
  padding-top: 12px;
  color: var(--wallo-color-text-muted);
  line-height: 1.7;
  font-size: 0.9rem;
}

.tax-deduction-remaining {
  color: var(--wallo-color-primary);
  font-weight: 700;
  font-size: 1rem;
}

.tax-strategy {
  margin-top: 0.85rem;
  padding: 0.75rem 0.85rem;
  border: 1px solid #e1edfb;
  border-radius: 14px;
  background: #f5faff;
}

.tax-strategy--reached {
  border-color: #d7eddf;
  background: #f1fbf4;
}

.tax-strategy-heading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.tax-strategy-heading strong {
  color: var(--wallo-color-text);
  font-size: 0.9rem;
}

.tax-strategy p {
  margin-top: 0.4rem;
  color: var(--wallo-color-text-muted);
  font-size: 0.86rem;
  line-height: 1.55;
}

.tax-guide {
  position: relative;
  flex-shrink: 0;
}

.tax-guide-toggle {
  gap: 0.35rem;
  padding: 0.25rem 0.45rem;
  color: var(--wallo-color-text-muted);
  font-size: 0.86rem;
}

.tax-guide-toggle:hover,
.tax-guide-toggle:focus-visible,
.tax-guide:focus-within .tax-guide-toggle {
  color: var(--wallo-color-primary);
}

.tax-guide-toggle :deep(.app-button__label) {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.tax-guide-panel {
  position: absolute;
  top: calc(100% + 0.5rem);
  right: 0;
  z-index: 20;
  width: min(420px, calc(100vw - 2rem));
  max-height: min(420px, calc(100vh - 2rem));
  overflow: auto;
  padding: 0.85rem 0.9rem;
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: 12px;
  color: var(--wallo-color-text-muted);
  background: #fbfdff;
  font-size: 0.86rem;
  line-height: 1.55;
  box-shadow: var(--wallo-shadow-card);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(-4px);
  transition:
    opacity 160ms ease,
    transform 160ms ease,
    visibility 160ms ease;
}

.tax-guide:hover .tax-guide-panel,
.tax-guide:focus-within .tax-guide-panel {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateY(0);
}

.tax-guide-section + .tax-guide-section {
  margin-top: 0.8rem;
  padding-top: 0.8rem;
  border-top: 1px solid var(--wallo-color-border-soft);
}

.tax-guide-section strong {
  display: block;
  margin-bottom: 0.25rem;
  color: var(--wallo-color-text);
  font-size: 0.86rem;
}

.tax-guide-section p {
  margin: 0;
}

.tax-guide-list {
  display: grid;
  gap: 0.25rem;
  margin: 0;
  padding-left: 1rem;
}

.tax-guide-list li.is-current {
  color: var(--wallo-color-primary);
  font-weight: 700;
}

@media (max-width: 991.98px) {
  .tax-deduction-body {
    min-height: auto;
    padding: var(--wallo-space-5);
  }
}

@media (max-width: 575.98px) {
  .tax-deduction-body {
    padding: var(--wallo-space-5) var(--wallo-space-4);
  }
}
</style>
