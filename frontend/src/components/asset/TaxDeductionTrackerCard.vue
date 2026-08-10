<script setup>
import { computed, onMounted } from "vue";
import { storeToRefs } from "pinia";
import { useReportStore } from "@/stores/assetReportStore.js";
import { formatWon } from "@/commonUtils/formatters";

const reportStore = useReportStore();
const { taxSettlement, isTaxSettlementLoading, taxSettlementError } =
  storeToRefs(reportStore);

const annualSalary = computed(() =>
  Number(taxSettlement.value?.annualSalary ?? 0),
);

const formattedAnnualSalary = computed(() =>
  annualSalary.value > 0 ? formatWon(annualSalary.value) : "조회 결과 없음",
);

const achievementRate = computed(() => {
  const spentAmount = Number(taxSettlement.value?.cardSpentYtd ?? 0);
  const threshold = Number(taxSettlement.value?.creditCardThreshold ?? 0);

  if (!Number.isFinite(spentAmount) || !Number.isFinite(threshold) || threshold <= 0) {
    return 0;
  }

  return Math.round((spentAmount / threshold) * 100);
});

const progressRate = computed(() =>
  Math.min(Math.max(achievementRate.value, 0), 100),
);

const achievementMessage = computed(() => {
  if (achievementRate.value >= 100) {
    return "연봉 25% 기준을 달성했습니다!";
  }

  if (achievementRate.value >= 90) {
    return "곧 카드 소득공제 기준을 채울 수 있어요!";
  }

  return `카드 사용액이 연봉 25% 기준의 ${achievementRate.value}%에 도달했어요.`;
});

const loadTaxSettlement = async () => {
  try {
    await reportStore.fetchTaxSettlement();
  } catch {
    // 조회 오류와 재시도 상태는 Pinia에서 관리합니다.
  }
};

const retryTaxSettlement = async () => {
  try {
    await reportStore.fetchTaxSettlement();
  } catch {
    // 재시도 결과는 Pinia 상태를 통해 카드에 표시합니다.
  }
};

onMounted(loadTaxSettlement);
</script>

<template>
  <article class="card tax-deduction-card h-100 border-0 shadow-sm">
    <div class="card-body tax-deduction-body">
      <h2 class="h5 fw-bold mb-0">소득공제 달성률</h2>

      <div
        v-if="isTaxSettlementLoading"
        class="tax-deduction-state text-center"
        aria-live="polite"
      >
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">소득공제 달성률을 불러오는 중</span>
        </div>
        <p class="text-secondary mb-0 mt-3">카드 사용 내역을 계산하고 있습니다.</p>
      </div>

      <div v-else-if="taxSettlementError" class="tax-deduction-state text-center">
        <i class="bi bi-exclamation-circle text-danger fs-2" aria-hidden="true"></i>
        <p class="fw-semibold mb-1 mt-3">소득공제 달성률을 불러오지 못했습니다.</p>
        <p class="small text-secondary mb-3">{{ taxSettlementError }}</p>
        <button
          type="button"
          class="btn btn-outline-danger"
          @click="retryTaxSettlement"
        >
          다시 시도
        </button>
      </div>

      <div v-else-if="taxSettlement" class="tax-deduction-content">
        <div class="annual-salary-summary mb-4">
          <span class="tax-deduction-label">자동 조회된 세전 연봉</span>
          <strong class="annual-salary-value">{{ formattedAnnualSalary }}</strong>
        </div>

        <div class="d-flex align-items-end justify-content-between gap-3 mb-3">
          <span class="tax-deduction-label">연봉 25% 달성률</span>
          <strong class="tax-deduction-rate">{{ achievementRate }}%</strong>
        </div>

        <div
          class="progress tax-deduction-progress"
          role="progressbar"
          aria-label="연봉 25% 달성률"
          :aria-valuenow="progressRate"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <div
            class="progress-bar"
            :style="{ width: `${progressRate}%` }"
          ></div>
        </div>

        <p class="tax-deduction-message mb-0">
          {{ achievementMessage }}
        </p>
      </div>

      <div v-else class="tax-deduction-state text-center">
        <i class="bi bi-wallet2 text-secondary fs-2" aria-hidden="true"></i>
        <p class="fw-semibold mb-1 mt-3">세전 연봉 정보가 없습니다.</p>
        <p class="small text-secondary mb-0">
          금융기관 연결을 완료하면 CODEF에서 세전 연봉을 자동 조회합니다.
        </p>
      </div>
    </div>
  </article>
</template>

<style scoped>
.tax-deduction-card {
  min-height: 310px;
  border-radius: 32px;
  background: #ffffff;
}

.tax-deduction-body {
  display: flex;
  min-height: 310px;
  flex-direction: column;
  padding: 36px 42px;
}

.tax-deduction-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding-top: 30px;
}

.annual-salary-summary {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.annual-salary-value {
  color: #5f50d2;
  font-size: 1.25rem;
}

.tax-deduction-state {
  display: flex;
  min-height: 220px;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.tax-deduction-label {
  color: #555b6e;
  font-weight: 600;
}

.tax-deduction-rate {
  color: #5f50d2;
  font-size: 1.35rem;
}

.tax-deduction-progress {
  height: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: #ebe8ff;
}

.tax-deduction-progress .progress-bar {
  border-radius: inherit;
  background: #6f5bd7;
}

.tax-deduction-message {
  padding-top: 18px;
  color: #555b6e;
  line-height: 1.7;
}

@media (max-width: 991.98px) {
  .tax-deduction-body {
    min-height: auto;
    padding: 30px;
  }
}

@media (max-width: 575.98px) {
  .tax-deduction-body {
    padding: 26px 22px;
  }

  .annual-salary-summary {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
