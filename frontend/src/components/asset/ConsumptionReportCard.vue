<script setup>
import { computed, onMounted } from "vue";
import { storeToRefs } from "pinia";
import { useReportStore } from "@/stores/reportStore";

const reportStore = useReportStore();
const { insight, isInsightLoading, insightError } = storeToRefs(reportStore);

const reportDescription = computed(() => {
  const content = insight.value?.reportContent ?? "";
  const callout = "소비 내역을 확인해 보세요!";

  if (!content.endsWith(callout)) {
    return { summary: content, callout: "" };
  }

  return {
    summary: content.slice(0, -callout.length).trim(),
    callout,
  };
});

const loadInsight = async () => {
  try {
    await reportStore.fetchInsight();
  } catch {
    // 사용자 안내와 인증 만료 이동은 Pinia 및 Axios 인터셉터에서 처리합니다.
  }
};

const retryInsight = async () => {
  try {
    await reportStore.retryInsight();
  } catch {
    // 재시도 오류는 Pinia에서 상태와 사용자 안내를 갱신합니다.
  }
};

onMounted(loadInsight);
</script>

<template>
  <article class="card consumption-report-card h-100 border-0 shadow-sm">
    <div class="card-body consumption-report-body">
      <h2 class="h5 fw-bold mb-0">소비 리포트</h2>

      <div
        v-if="isInsightLoading"
        class="report-state text-center"
        aria-live="polite"
      >
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">소비 리포트를 불러오는 중</span>
        </div>
        <p class="text-secondary mb-0 mt-3">소비 내역을 분석하고 있습니다.</p>
      </div>

      <div v-else-if="insightError" class="report-state">
        <i class="bi bi-exclamation-circle text-danger fs-2" aria-hidden="true"></i>
        <p class="fw-semibold mb-1 mt-3">소비 리포트를 불러오지 못했습니다.</p>
        <p class="small text-secondary text-center mb-3">{{ insightError }}</p>
        <button type="button" class="btn btn-outline-danger" @click="retryInsight">
          다시 시도
        </button>
      </div>

      <div v-else-if="insight" class="report-content">
        <div class="report-alert d-flex align-items-start gap-2">
          <i
            class="bi bi-exclamation-triangle-fill report-warning-icon"
            aria-hidden="true"
          ></i>
          <strong>{{ insight.reportTitle }}</strong>
        </div>

        <p class="report-description mb-0">
          <span>{{ reportDescription.summary }}</span>
          <span v-if="reportDescription.callout" class="d-block">
            {{ reportDescription.callout }}
          </span>
        </p>

        <RouterLink to="/assets/expenses" class="report-detail-link">
          월별 리포트 보기
          <span aria-hidden="true">&gt;</span>
        </RouterLink>
      </div>

      <div v-else class="report-state text-center">
        <i class="bi bi-clipboard-data text-secondary fs-2" aria-hidden="true"></i>
        <p class="text-secondary mb-0 mt-3">분석할 소비 데이터가 없습니다.</p>
      </div>
    </div>
  </article>
</template>

<style scoped>
.consumption-report-card {
  min-height: 310px;
  border-radius: 32px;
  background: #ffffff;
}

.consumption-report-body {
  display: flex;
  min-height: 310px;
  flex-direction: column;
  padding: 36px 42px;
}

.report-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding-top: 30px;
}

.report-alert {
  color: #dc3545;
  font-size: 1.08rem;
  line-height: 1.5;
}

.report-warning-icon {
  flex: 0 0 auto;
  margin-top: 2px;
}

.report-description {
  max-width: 520px;
  padding-top: 18px;
  color: #555b6e;
  line-height: 1.7;
}

.report-detail-link {
  align-self: flex-start;
  margin-top: auto;
  padding-top: 24px;
  color: #5f50d2;
  font-weight: 700;
  text-decoration: none;
}

.report-detail-link:hover,
.report-detail-link:focus {
  color: #3f31ad;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.report-state {
  display: flex;
  min-height: 220px;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

@media (max-width: 991.98px) {
  .consumption-report-body {
    min-height: auto;
    padding: 30px;
  }
}

@media (max-width: 575.98px) {
  .consumption-report-body {
    padding: 26px 22px;
  }
}
</style>
