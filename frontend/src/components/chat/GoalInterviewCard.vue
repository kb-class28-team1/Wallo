<script setup>
import { computed } from "vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"

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

const isReviewable = computed(() => ["CONFIRMATION", "REVIEW"].includes(draft.value.state))

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
}

const missingFieldLabels = computed(() =>
  (draft.value.missingFields ?? []).map((field) => fieldLabels[field] ?? field),
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
    CALCULATED: "월 필요액 계산 완료",
    ACHIEVABLE: "달성 가능",
    TIGHT: "여유가 적음",
    ADJUSTMENT_REQUIRED: "조정 필요",
    ALREADY_ACHIEVED: "이미 달성",
    INSUFFICIENT_INFORMATION: "정보 부족",
  }
  return labels[feasibility.value?.status] ?? "계획 확인 중"
})

const feasibilityClass = computed(() => {
  if (feasibility.value?.status === "CALCULATED") return "text-primary"
  if (feasibility.value?.status === "ACHIEVABLE") return "text-success"
  if (feasibility.value?.status === "ADJUSTMENT_REQUIRED") return "text-danger"
  return "text-warning-emphasis"
})
</script>

<template>
  <AppCard
    as="section"
    class="goal-interview-card"
    padding="none"
    aria-labelledby="goal-interview-title"
  >
    <div class="goal-interview-body">
      <div>
        <h2 id="goal-interview-title" class="h6 mb-0 fw-bold">
          {{ draft.title || "새 금융 목표" }}
        </h2>
      </div>

      <AppAlert v-if="isCancelled" class="goal-cancelled-message" variant="neutral" role="status">
        목표 설정을 취소했습니다.
      </AppAlert>

      <template v-if="!isCancelled">
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
        </dl>

        <div v-if="feasibility" class="feasibility-box mt-3">
          <div class="d-flex justify-content-between align-items-center gap-2">
            <span class="small text-secondary">계산 상태</span>
            <strong :class="feasibilityClass">{{ feasibilityLabel }}</strong>
          </div>
          <div
            v-if="
              feasibility.requiredMonthlyAmount !== null &&
              feasibility.requiredMonthlyAmount !== undefined
            "
            class="d-flex justify-content-between align-items-center gap-2 small mt-2"
          >
            <span class="text-secondary">월 필요 납입액</span>
            <strong class="text-dark">{{ formatAmount(feasibility.requiredMonthlyAmount) }}</strong>
          </div>
        </div>

        <div v-if="missingFieldLabels.length" class="mt-3 small text-secondary">
          다음 정보를 알려주시면 계획을 계산할 수 있어요:
          <span class="fw-semibold">{{ missingFieldLabels.join(", ") }}</span>
        </div>

        <div v-if="isReviewable && !isCompleted" class="d-flex gap-2 mt-3">
          <AppButton
            class="btn-primary goal-confirm-button flex-grow-1"
            variant="primary"
            :disabled="loading"
            :loading="loading"
            @click="emit('confirm')"
          >
            이대로 확정
          </AppButton>
          <AppButton
            class="btn-outline-secondary goal-cancel-button"
            variant="secondary"
            :disabled="loading"
            @click="emit('cancel')"
          >
            취소
          </AppButton>
        </div>
      </template>
    </div>
  </AppCard>
</template>

<style scoped>
.goal-interview-card {
  margin: 4px 0 18px;
  border-left: 4px solid var(--wallo-color-primary) !important;
}

.goal-interview-body {
  padding: var(--wallo-space-4);
}

.goal-cancelled-message {
  margin-top: var(--wallo-space-3);
}

.goal-details dt {
  color: var(--wallo-color-text-muted);
  font-size: 0.75rem;
  font-weight: 500;
}

.goal-details dd {
  margin: 2px 0 0;
  color: var(--wallo-color-text);
  font-size: 0.9rem;
  font-weight: 700;
}

.feasibility-box {
  padding: 10px 12px;
  background: var(--wallo-color-surface-soft);
  border-radius: var(--wallo-radius-sm);
}
</style>
