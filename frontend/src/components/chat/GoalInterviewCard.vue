<script setup>
import { computed } from "vue"

const props = defineProps({
  interview: {
    type: Object,
    required: true,
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(["confirm", "cancel"])

const draft = computed(() => props.interview?.draft ?? {})
const feasibility = computed(() => props.interview?.feasibility ?? null)

const isReviewable = computed(() =>
  ["CONFIRMATION", "REVIEW"].includes(draft.value.state),
)

const isCompleted = computed(
  () => props.interview?.action === "CONFIRM" || draft.value.state === "COMPLETED",
)

const isCancelled = computed(
  () => props.interview?.action === "CANCEL" || draft.value.state === "CANCELLED",
)

const fieldLabels = {
  goalType: "목표 유형",
  targetAmount: "목표 금액",
  targetDate: "목표 날짜",
  currentAmount: "현재 준비금",
  monthlyContribution: "월 납입 가능액",
}

const missingFieldLabels = computed(() =>
  (draft.value.missingFields ?? []).map(
    (field) => fieldLabels[field] ?? field,
  ),
)

const formatAmount = (amount) => {
  if (amount === null || amount === undefined || amount === "") return "-"
  return `${Number(amount).toLocaleString("ko-KR")}원`
}

const formatDate = (date) => {
  if (!date) return "-"
  return String(date).replaceAll("-", ".")
}

const feasibilityLabel = computed(() => {
  const labels = {
    ACHIEVABLE: "달성 가능",
    TIGHT: "여유가 적음",
    ADJUSTMENT_REQUIRED: "조정 필요",
    ALREADY_ACHIEVED: "이미 달성",
    INSUFFICIENT_INFORMATION: "정보 부족",
  }
  return labels[feasibility.value?.status] ?? "계획 확인 중"
})

const feasibilityClass = computed(() => {
  if (feasibility.value?.status === "ACHIEVABLE") return "text-success"
  if (feasibility.value?.status === "ADJUSTMENT_REQUIRED") return "text-danger"
  return "text-warning-emphasis"
})
</script>

<template>
  <section
    class="goal-interview-card card border-0 shadow-sm"
    aria-labelledby="goal-interview-title"
  >
    <div class="card-body p-3 p-md-4">
      <div class="d-flex align-items-start gap-3">
        <div class="goal-icon flex-shrink-0" aria-hidden="true">
          <i class="bi bi-bullseye"></i>
        </div>
        <div class="flex-grow-1">
          <p class="mb-1 small fw-semibold text-primary">AI 목표 설정</p>
          <h2 id="goal-interview-title" class="h6 mb-0 fw-bold">
            {{ draft.title || "새 금융 목표" }}
          </h2>
        </div>
      </div>

      <div v-if="isCompleted" class="alert alert-success mt-3 mb-0 py-2" role="status">
        목표가 저장되었습니다.
      </div>

      <div v-else-if="isCancelled" class="alert alert-secondary mt-3 mb-0 py-2" role="status">
        목표 설정을 취소했습니다.
      </div>

      <template v-else>
        <dl class="goal-details row g-2 mb-0 mt-3">
          <div class="col-6">
            <dt>목표 금액</dt>
            <dd>{{ formatAmount(draft.targetAmount) }}</dd>
          </div>
          <div class="col-6">
            <dt>목표 날짜</dt>
            <dd>{{ formatDate(draft.targetDate) }}</dd>
          </div>
          <div class="col-6">
            <dt>현재 준비금</dt>
            <dd>{{ formatAmount(draft.currentAmount) }}</dd>
          </div>
          <div class="col-6">
            <dt>월 납입 가능액</dt>
            <dd>{{ formatAmount(draft.monthlyContribution) }}</dd>
          </div>
        </dl>

        <div v-if="feasibility" class="feasibility-box mt-3">
          <div class="d-flex justify-content-between align-items-center gap-2">
            <span class="small text-secondary">달성 가능성</span>
            <strong :class="feasibilityClass">{{ feasibilityLabel }}</strong>
          </div>
          <div
            v-if="feasibility.requiredMonthlyAmount !== null && feasibility.requiredMonthlyAmount !== undefined"
            class="small text-secondary mt-1"
          >
            목표일까지 매달 약
            <strong class="text-dark">{{ formatAmount(feasibility.requiredMonthlyAmount) }}</strong>이 필요합니다.
          </div>
        </div>

        <div v-if="missingFieldLabels.length" class="mt-3 small text-secondary">
          다음 정보를 알려주시면 계획을 계산할 수 있어요:
          <span class="fw-semibold">{{ missingFieldLabels.join(", ") }}</span>
        </div>

        <div v-if="isReviewable" class="d-flex gap-2 mt-3">
          <button
            type="button"
            class="btn btn-primary flex-grow-1"
            :disabled="loading"
            @click="emit('confirm')"
          >
            <span v-if="loading" class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
            이대로 확정
          </button>
          <button
            type="button"
            class="btn btn-outline-secondary"
            :disabled="loading"
            @click="emit('cancel')"
          >
            취소
          </button>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.goal-interview-card {
  margin: 4px 0 18px;
  border-left: 4px solid #7062de !important;
}

.goal-icon {
  display: grid;
  width: 38px;
  height: 38px;
  color: #7062de;
  background: #efedff;
  border-radius: 12px;
  place-items: center;
  font-size: 1.1rem;
}

.goal-details dt {
  color: #7b849b;
  font-size: 0.75rem;
  font-weight: 500;
}

.goal-details dd {
  margin: 2px 0 0;
  color: #29273a;
  font-size: 0.9rem;
  font-weight: 700;
}

.feasibility-box {
  padding: 10px 12px;
  background: #f7f6ff;
  border-radius: 10px;
}

.goal-interview-card .btn-primary {
  --bs-btn-bg: #7062de;
  --bs-btn-border-color: #7062de;
  --bs-btn-hover-bg: #5f52c9;
  --bs-btn-hover-border-color: #5f52c9;
}
</style>
