<script setup>
import { computed, onMounted, ref, watch } from "vue"
import { storeToRefs } from "pinia"
import { useReportStore } from "@/stores/assetReportStore.js"
import {
  CONSUMPTION_REPORT_FALLBACK_IMAGE,
  getConsumptionReportImage,
  getConsumptionReportImageAlt,
} from "@/features/asset/consumptionReportImages.js"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppState from "@/components/ui/AppState.vue"

const reportStore = useReportStore()
const props = defineProps({
  forceRefresh: {
    type: Boolean,
    default: false,
  },
})
const { insight, initialInsightLoading, refreshingInsight, isInsightLoading, insightError } =
  storeToRefs(reportStore)
const reportImageLoadFailed = ref(false)

const isInitialLoading = computed(
  () => initialInsightLoading?.value ?? Boolean(isInsightLoading?.value && !insight.value),
)
const isRefreshing = computed(() => refreshingInsight?.value ?? false)

const isFallbackInsight = computed(() => insight.value?.generationMode === "FALLBACK")
const categoryReportImage = computed(() => getConsumptionReportImage(insight.value?.category))
const usesFallbackImage = computed(
  () =>
    isFallbackInsight.value ||
    reportImageLoadFailed.value ||
    categoryReportImage.value === CONSUMPTION_REPORT_FALLBACK_IMAGE,
)
const reportImage = computed(() =>
  usesFallbackImage.value ? CONSUMPTION_REPORT_FALLBACK_IMAGE : categoryReportImage.value,
)
const reportImageAlt = computed(() =>
  usesFallbackImage.value
    ? "소비 리포트를 준비 중인 이미지"
    : getConsumptionReportImageAlt(insight.value?.category),
)

const handleReportImageError = () => {
  reportImageLoadFailed.value = true
}

watch(
  () => [insight.value?.category, insight.value?.generationMode],
  () => {
    reportImageLoadFailed.value = false
  },
)

const REPORT_CALLOUTS = [
  "지출 내역을 점검해보세요.",
  "가벼운 점검 해보세요 😊",
  "소비 내역을 확인해 보세요!",
]

const reportDescription = computed(() => {
  const content = insight.value?.reportContent ?? ""
  const callout = REPORT_CALLOUTS.find((item) => content.endsWith(item))

  if (!callout) {
    return { summary: content, callout: "" }
  }

  return {
    summary: content.slice(0, -callout.length).trim(),
    callout,
  }
})

const loadInsight = async ({ force = false } = {}) => {
  try {
    await reportStore.fetchInsight({ force })
  } catch {
    // 사용자 안내와 인증 만료 이동은 Pinia 및 Axios 인터셉터에서 처리합니다.
  }
}

const retryInsight = async () => {
  try {
    await reportStore.fetchInsight({ force: true })
  } catch {
    // 재시도 오류는 Pinia에서 상태와 사용자 안내를 갱신합니다.
  }
}

onMounted(() => loadInsight({ force: props.forceRefresh }))
</script>

<template>
  <AppCard class="consumption-report-card h-100" padding="none">
    <div class="consumption-report-body">
      <div class="consumption-report-header">
        <div class="d-flex align-items-start justify-content-between gap-3">
          <h2 class="h5 fw-bold mb-2">소비 리포트</h2>
          <RouterLink to="/assets/expenses" class="btn app-action-link pressable">
            더보기
            <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
          </RouterLink>
        </div>

        <div class="report-refresh-status" role="status" aria-live="polite">
          <template v-if="isRefreshing">
            <span
              class="spinner-border spinner-border-sm text-primary me-2"
              aria-hidden="true"
            ></span>
            소비 리포트를 최신 상태로 갱신하고 있습니다.
          </template>
        </div>
      </div>

      <AppState
        v-if="isInitialLoading"
        class="report-state"
        type="loading"
        title="소비 리포트를 불러오는 중입니다."
        message="소비 내역을 분석하고 있습니다."
        compact
      />

      <AppState
        v-else-if="insightError && !insight"
        class="report-state"
        type="error"
        title="소비 리포트를 불러오지 못했습니다."
        :message="insightError"
        action-text="다시 시도"
        action-variant="danger"
        compact
        @action="retryInsight"
      >
        <template #icon>
          <img
            :src="CONSUMPTION_REPORT_FALLBACK_IMAGE"
            alt="소비 리포트를 불러오지 못함"
            class="report-state-image"
            @error="handleReportImageError"
          />
        </template>
      </AppState>

      <div v-else-if="insight" class="report-content" :class="{ 'has-report-image': reportImage }">
        <AppAlert
          v-if="insightError"
          class="report-sync-alert"
          variant="warning"
          :show-icon="false"
        >
          <span>최신 리포트를 갱신하지 못했습니다. 기존 리포트를 표시하고 있습니다.</span>
          <AppButton variant="outline" size="sm" @click="retryInsight">다시 시도</AppButton>
        </AppAlert>

        <img
          v-if="reportImage"
          :src="reportImage"
          :alt="reportImageAlt"
          class="report-category-image"
          @error="handleReportImageError"
        />

        <div class="report-alert d-flex align-items-start gap-2">
          <i class="bi bi-exclamation-triangle-fill report-warning-icon" aria-hidden="true"></i>
          <strong>{{ insight.reportTitle }}</strong>
        </div>

        <p class="report-description mb-0">
          <span>{{ reportDescription.summary }}</span>
          <span v-if="reportDescription.callout" class="d-block">
            {{ reportDescription.callout }}
          </span>
        </p>

      </div>

      <AppState
        v-else
        class="report-state"
        type="empty"
        title="분석할 소비 데이터가 없습니다."
        message="소비 내역이 쌓이면 맞춤형 리포트를 확인할 수 있습니다."
        compact
      />
    </div>
  </AppCard>
</template>

<style scoped>
.consumption-report-card {
  min-height: 310px;
  border-radius: var(--wallo-radius-xl);
}

.consumption-report-body {
  display: flex;
  min-height: 310px;
  flex-direction: column;
  padding: var(--wallo-space-6);
}

.report-refresh-status {
  display: flex;
  min-height: 48px;
  align-items: flex-start;
  color: var(--wallo-color-text-muted);
  font-size: 0.875rem;
  line-height: 1.5;
}

.report-sync-alert {
  margin-bottom: var(--wallo-space-4);
}

.report-content {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
}

.report-content.has-report-image {
  padding-right: clamp(175px, 24vw, 220px);
}

.report-category-image {
  position: absolute;
  right: 0;
  bottom: 0;
  width: clamp(150px, 24vw, 210px);
  height: clamp(150px, 24vw, 210px);
  object-fit: contain;
}

.report-alert {
  color: var(--wallo-color-danger);
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
  color: var(--wallo-color-text-muted);
  line-height: 1.7;
  word-break: keep-all;
  overflow-wrap: break-word;
}

.report-content.has-report-image .report-description {
  max-width: none;
}

.report-state {
  display: flex;
  min-height: 220px;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.report-state-image {
  display: block;
  width: 80px;
  height: 80px;
  margin-bottom: 4px;
  object-fit: contain;
}

@media (max-width: 991.98px) {
  .consumption-report-body {
    min-height: auto;
    padding: 30px;
  }
}

@media (max-width: 575.98px) {
  .consumption-report-body {
    min-height: auto;
    padding: 26px 22px;
  }

  .report-category-image {
    right: 0;
    bottom: 0;
    width: 145px;
    height: 145px;
  }

  .report-content.has-report-image .report-description {
    max-width: none;
  }

  .report-content.has-report-image {
    padding-right: 0;
    padding-bottom: 150px;
  }
}
</style>
