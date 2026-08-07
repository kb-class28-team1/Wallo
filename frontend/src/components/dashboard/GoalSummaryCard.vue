<script setup>
import { formatWon } from "@/commonUtils/formatters";

defineProps({
  goals: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  error: {
    type: String,
    default: null,
  },
});

const emit = defineEmits(["retry"]);

const statusLabels = {
  ACTIVE: "진행 중",
  ACHIEVED: "달성 완료",
  CANCELLED: "취소됨",
};

const formatGoalDate = (date) => {
  if (!date) {
    return "-";
  }

  const parsedDate = new Date(
    String(date).length === 10 ? `${date}T00:00:00` : date,
  );

  if (Number.isNaN(parsedDate.getTime())) {
    return date;
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(parsedDate);
};

const getStatusLabel = (status) => statusLabels[status] ?? status ?? "상태 미정";
</script>

<template>
  <article class="card goal-summary-card border-0 shadow-sm">
    <div class="card-body">
      <div class="d-flex align-items-start justify-content-between gap-3 mb-4">
        <div>
          <p class="goal-label fw-semibold mb-2">AI 금융 목표</p>
          <h2 class="h4 fw-bold mb-0">확정된 목표</h2>
        </div>

        <RouterLink to="/ai-consulting" class="btn dashboard-action-button flex-shrink-0">
          목표 설정
          <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
        </RouterLink>
      </div>

      <div v-if="loading" class="goal-state text-secondary" role="status">
        <span class="spinner-border spinner-border-sm text-primary me-2" aria-hidden="true"></span>
        목표 정보를 불러오는 중입니다.
      </div>

      <div v-else-if="error" class="alert alert-danger mb-0" role="alert">
        <p class="mb-3">{{ error }}</p>
        <button type="button" class="btn btn-sm btn-outline-danger" @click="emit('retry')">
          다시 시도
        </button>
      </div>

      <div v-else-if="goals.length === 0" class="goal-state text-secondary">
        <i class="bi bi-bullseye fs-2 d-block mb-2" aria-hidden="true"></i>
        <p class="mb-1 fw-semibold text-dark">아직 확정된 금융 목표가 없습니다.</p>
        <p class="mb-0">AI 컨설팅에서 목표를 설정해보세요.</p>
      </div>

      <div v-else class="goal-list">
        <section
          v-for="(goal, index) in goals"
          :key="goal.goalId ?? goal.conversationId ?? index"
          class="goal-item"
        >
          <div class="d-flex align-items-start justify-content-between gap-3 mb-3">
            <div>
              <p class="goal-item-label mb-1">금융 목표</p>
              <h3 class="h5 fw-bold mb-0">{{ goal.title || "제목 없는 목표" }}</h3>
            </div>
            <span class="badge rounded-pill goal-status-badge">
              {{ getStatusLabel(goal.status) }}
            </span>
          </div>

          <dl class="row gy-3 mb-0">
            <div class="col-sm-4">
              <dt>목표 금액</dt>
              <dd>{{ formatWon(goal.targetAmount) }}</dd>
            </div>
            <div class="col-sm-4">
              <dt>목표 날짜</dt>
              <dd>{{ formatGoalDate(goal.targetDate) }}</dd>
            </div>
            <div class="col-sm-4">
              <dt>월 필요 납입액</dt>
              <dd>{{ formatWon(goal.requiredMonthlyAmount) }}</dd>
            </div>
          </dl>
        </section>
      </div>
    </div>
  </article>
</template>

<style scoped>
.goal-summary-card {
  max-width: 1080px;
  border-radius: 32px;
  background: #ffffff;
}

.goal-label,
.goal-item-label {
  color: #111111;
}

.goal-label {
  font-size: 1.1rem;
}

.dashboard-action-button {
  border: 1px solid #0000d5;
  border-radius: 14px;
  color: #0000d5;
  background: #ffffff;
  transition: color 0.2s ease, background-color 0.2s ease;
}

.dashboard-action-button:hover,
.dashboard-action-button:focus {
  border-color: #0000d5;
  color: #ffffff;
  background: #0000d5;
}

.goal-state {
  min-height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.goal-item + .goal-item {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #e9ecef;
}

.goal-item dt {
  margin-bottom: 0.35rem;
  color: #6c757d;
  font-size: 0.875rem;
  font-weight: 500;
}

.goal-item dd {
  margin-bottom: 0;
  color: #111111;
  font-weight: 700;
}

.goal-status-badge {
  color: #0000d5;
  background: #eef0ff;
  white-space: nowrap;
}

@media (max-width: 575.98px) {
  .goal-summary-card .card-body {
    padding: 1.5rem;
  }

  .goal-summary-card .dashboard-action-button {
    padding: 0.35rem 0.6rem;
    font-size: 0.8rem;
  }
}
</style>
