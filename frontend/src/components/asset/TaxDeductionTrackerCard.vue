<script setup>
import { ref } from "vue";

const achievementRate = 92;
const salaryModalVisible = ref(false);
const annualSalary = ref(50_000_000);
const annualSalaryInput = ref("");

const formatNumber = (amount = 0) => new Intl.NumberFormat("ko-KR").format(amount);

const openSalaryModal = () => {
  annualSalaryInput.value = formatNumber(annualSalary.value);
  salaryModalVisible.value = true;
};

const closeSalaryModal = () => {
  salaryModalVisible.value = false;
};

const formatSalaryInput = () => {
  const numericValue = String(annualSalaryInput.value).replace(/[^0-9]/g, "");

  annualSalaryInput.value = numericValue ? formatNumber(numericValue) : "";
};

const saveSalary = () => {
  const salary = Number(String(annualSalaryInput.value).replaceAll(",", ""));

  if (!Number.isFinite(salary) || salary <= 0) {
    alert("연봉은 0원보다 큰 금액으로 입력해 주세요.");
    return;
  }

  annualSalary.value = salary;
  closeSalaryModal();
};
</script>

<template>
  <article class="card tax-deduction-card h-100 border-0 shadow-sm">
    <div class="card-body tax-deduction-body">
      <h2 class="h5 fw-bold mb-0">소득공제 달성률</h2>

      <div class="tax-deduction-content">
        <div class="d-flex align-items-end justify-content-between gap-3 mb-3">
          <span class="tax-deduction-label">연봉 25% 도달률</span>
          <strong class="tax-deduction-rate">{{ achievementRate }}%</strong>
        </div>

        <div
          class="progress tax-deduction-progress"
          role="progressbar"
          aria-label="연봉 25% 도달률"
          :aria-valuenow="achievementRate"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <div
            class="progress-bar"
            :style="{ width: `${achievementRate}%` }"
          ></div>
        </div>

        <p class="tax-deduction-message mb-0">
          곧 신용카드 공제 한도를 채웁니다!
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
          <h2 id="salaryModalTitle" class="modal-title h5 fw-bold">연봉 수정</h2>
          <button
            type="button"
            class="btn-close"
            aria-label="닫기"
            @click="closeSalaryModal"
          ></button>
        </div>

        <form @submit.prevent="saveSalary">
          <div class="modal-body">
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
                @input="formatSalaryInput"
              />
              <span class="input-group-text">원</span>
            </div>
            <p class="small text-secondary mb-0 mt-2">
              입력한 연봉을 기준으로 신용카드 소득공제 도달률을 계산합니다.
            </p>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-light" @click="closeSalaryModal">
              취소
            </button>
            <button type="submit" class="btn btn-primary">저장</button>
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
