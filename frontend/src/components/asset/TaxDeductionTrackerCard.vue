<script setup>
import { computed, onMounted, ref } from "vue";
import { storeToRefs } from "pinia";
import { useReportStore } from "@/stores/reportStore";
import { formatNumber } from "@/utils/formatters";

const reportStore = useReportStore();
const {
  taxSettlement,
  isTaxSettlementLoading,
  taxSettlementError,
  isAnnualSalarySaving,
  annualSalaryError,
} = storeToRefs(reportStore);
const salaryModalVisible = ref(false);
const annualSalaryInput = ref("");

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

const salaryModalTitle = computed(() =>
  Number(taxSettlement.value?.annualSalary ?? 0) > 0 ? "연봉 수정" : "연봉 입력",
);

const achievementMessage = computed(() => {
  if (achievementRate.value >= 100) {
    return "연봉 25% 기준을 달성했습니다!";
  }

  if (achievementRate.value >= 90) {
    return "곧 카드 소득공제 기준을 채웁니다!";
  }

  return `카드 사용액이 연봉 25% 기준의 ${achievementRate.value}%에 도달했어요.`;
});

const loadTaxSettlement = async () => {
  try {
    await reportStore.fetchTaxSettlement();
  } catch {
    // 오류 상태와 연봉 입력 필요 상태는 Pinia에서 각각 처리합니다.
  }
};

const retryTaxSettlement = async () => {
  try {
    await reportStore.retryTaxSettlement();
  } catch {
    // 다시 시도 결과는 Pinia 상태를 통해 카드에 표시합니다.
  }
};

const openSalaryModal = () => {
  const currentSalary = Number(taxSettlement.value?.annualSalary ?? 0);

  annualSalaryInput.value = currentSalary > 0 ? formatNumber(currentSalary) : "";
  reportStore.annualSalaryError = null;
  salaryModalVisible.value = true;
};

const closeSalaryModal = () => {
  if (isAnnualSalarySaving.value) {
    return;
  }

  salaryModalVisible.value = false;
};

const formatSalaryInput = () => {
  const numericValue = String(annualSalaryInput.value).replace(/[^0-9]/g, "");

  annualSalaryInput.value = numericValue ? formatNumber(numericValue) : "";
};

const saveSalary = async () => {
  const salary = Number(String(annualSalaryInput.value).replaceAll(",", ""));

  if (!Number.isFinite(salary) || salary <= 0) {
    reportStore.annualSalaryError = "연봉은 0원보다 큰 금액으로 입력해 주세요.";
    return;
  }

  try {
    await reportStore.saveAnnualSalary(salary);
    salaryModalVisible.value = false;
  } catch {
    // 저장 오류는 모달 안에서 안내합니다.
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
        <div class="d-flex align-items-end justify-content-between gap-3 mb-3">
          <span class="tax-deduction-label">연봉 25% 도달률</span>
          <strong class="tax-deduction-rate">{{ achievementRate }}%</strong>
        </div>

        <div
          class="progress tax-deduction-progress"
          role="progressbar"
          aria-label="연봉 25% 도달률"
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

        <button
          type="button"
          class="btn salary-edit-button"
          @click="openSalaryModal"
        >
          연봉 수정하기
          <span aria-hidden="true">&gt;</span>
        </button>
      </div>

      <div v-else class="tax-deduction-state text-center">
        <i class="bi bi-wallet2 text-secondary fs-2" aria-hidden="true"></i>
        <p class="fw-semibold mb-1 mt-3">연봉 정보가 필요합니다.</p>
        <p class="small text-secondary mb-3">
          연봉을 입력하면 카드 소득공제 도달률을 계산할 수 있습니다.
        </p>
        <button type="button" class="btn btn-primary" @click="openSalaryModal">
          연봉 입력하기
        </button>
      </div>
    </div>
  </article>

  <div v-if="salaryModalVisible" class="modal-backdrop fade show"></div>
  <div
    v-if="salaryModalVisible"
    class="modal fade show d-block"
    tabindex="-1"
    role="dialog"
    aria-modal="true"
    aria-labelledby="salaryModalTitle"
    @click.self="closeSalaryModal"
    @keydown.esc="closeSalaryModal"
  >
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
        <div class="modal-header">
          <h2 id="salaryModalTitle" class="modal-title h5 fw-bold">
            {{ salaryModalTitle }}
          </h2>
          <button
            type="button"
            class="btn-close"
            aria-label="닫기"
            @click="closeSalaryModal"
          ></button>
        </div>

        <form @submit.prevent="saveSalary">
          <div class="modal-body">
            <div
              v-if="annualSalaryError"
              class="alert alert-danger py-2"
              role="alert"
            >
              {{ annualSalaryError }}
            </div>

            <label for="annualSalary" class="form-label fw-semibold">연봉</label>
            <div class="input-group">
              <input
                id="annualSalary"
                v-model="annualSalaryInput"
                type="text"
                class="form-control"
                inputmode="numeric"
                autocomplete="off"
                required
                :disabled="isAnnualSalarySaving"
                @input="formatSalaryInput"
              />
              <span class="input-group-text">원</span>
            </div>
            <p class="small text-secondary mb-0 mt-2">
              입력한 연봉을 기준으로 신용카드 소득공제 도달률을 계산합니다.
            </p>
          </div>

          <div class="modal-footer">
            <button
              type="button"
              class="btn btn-light"
              :disabled="isAnnualSalarySaving"
              @click="closeSalaryModal"
            >
              취소
            </button>
            <button
              type="submit"
              class="btn btn-primary"
              :disabled="isAnnualSalarySaving"
            >
              <span
                v-if="isAnnualSalarySaving"
                class="spinner-border spinner-border-sm me-2"
                aria-hidden="true"
              ></span>
              {{ isAnnualSalarySaving ? "저장 중..." : "저장" }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
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

.salary-edit-button {
  align-self: flex-end;
  margin-top: auto;
  padding: 24px 0 0;
  border: 0;
  color: #5f50d2;
  font-weight: 700;
  text-decoration: none;
}

.salary-edit-button:hover,
.salary-edit-button:focus {
  color: #3f31ad;
  text-decoration: underline;
  text-underline-offset: 4px;
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
}
</style>
