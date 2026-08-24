<script setup>
import { computed, onMounted, ref } from "vue"
import { useRouter } from "vue-router"
import { storeToRefs } from "pinia"
import { getLatestAnalysisResults } from "@/api/analysisResultApi"
import AnalysisResult from "@/components/analysis/AnalysisResult.vue"
import AssetAnalysisResult from "@/components/analysis/AssetAnalysisResult.vue"
import ProductRecommendationResult from "@/components/analysis/ProductRecommendationResult.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"
import AppTabs from "@/components/ui/AppTabs.vue"
import { useProductRecommendationStore } from "@/stores/productRecommendationStore"

const router = useRouter()
const productRecommendationStore = useProductRecommendationStore()
const {
  productRecommendation,
  requestMessage: productRequestMessage,
  generatedAt: productGeneratedAt,
  status: productStatus,
  error: productError,
} = storeToRefs(productRecommendationStore)

const latestResults = ref(null)
const isLoading = ref(false)
const error = ref("")
const activeAnalysis = ref("asset")
const analysisTabs = [
  { value: "asset", label: "자산분석", icon: "bi-wallet2" },
  { value: "consumption", label: "소비분석", icon: "bi-graph-down-arrow" },
  { value: "product", label: "금융상품추천", icon: "bi-stars" },
]
const assetResult = computed(() => latestResults.value?.asset ?? null)
const consumptionResult = computed(() => latestResults.value?.consumption ?? null)

const formatDate = (value) => {
  if (!value) return ""
  return String(value).replace("T", " ").slice(0, 16)
}

const loadResults = async () => {
  isLoading.value = true
  error.value = ""
  try {
    latestResults.value = await getLatestAnalysisResults()
  } catch (caughtError) {
    latestResults.value = null
    error.value = caughtError.message || "최신 분석 결과를 불러오지 못했습니다."
    alert(error.value)
  } finally {
    isLoading.value = false
  }
}

const refreshAll = async () => {
  await Promise.all([
    loadResults(),
    productRecommendationStore.fetchLatest({ force: true }),
  ])
}

const startAnalysis = (action) => router.push({ name: "chat", query: { action } })

onMounted(refreshAll)
</script>

<template>
  <section class="analysis-dashboard-page">
    <AppPageHeader
      title="AI 분석 결과"
    >
      <template #actions>
        <button
          type="button"
        class="btn app-action-link pressable"
          data-testid="analysis-refresh-button"
          :disabled="isLoading"
          @click="refreshAll"
        >
          <i class="bi bi-arrow-clockwise me-1" aria-hidden="true"></i>
          새로고침
        </button>
      </template>
    </AppPageHeader>

    <AppState
      v-if="isLoading"
      class="analysis-page-state"
      type="loading"
      title="최신 분석 결과를 불러오는 중입니다."
      message="저장된 자산과 소비 분석을 확인하고 있어요."
    />

    <AppAlert v-else-if="error" variant="warning">
      <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
        <span>{{ error }}</span>
        <AppButton variant="outline" size="sm" @click="loadResults">다시 시도</AppButton>
      </div>
    </AppAlert>

    <div v-else>
      <AppTabs
        v-model="activeAnalysis"
        class="analysis-switcher"
        :items="analysisTabs"
        variant="pill"
        aria-label="AI 분석 결과 선택"
        full-width
      >
        <template #tab="{ item }">
          <i :class="`bi ${item.icon}`" aria-hidden="true"></i>
          <span>{{ item.label }}</span>
        </template>
      </AppTabs>

      <AppCard class="analysis-section-card" padding="none">
        <div class="analysis-meta-bar">
          <small v-if="activeAnalysis === 'asset' && assetResult?.generatedAt">
            <i class="bi bi-clock me-1" aria-hidden="true"></i>
            {{ formatDate(assetResult.generatedAt) }} 업데이트
          </small>
          <small v-else-if="activeAnalysis === 'consumption' && consumptionResult?.generatedAt">
            <i class="bi bi-clock me-1" aria-hidden="true"></i>
            {{ formatDate(consumptionResult.generatedAt) }} 업데이트
          </small>
          <small v-else-if="activeAnalysis === 'product' && productGeneratedAt">
            <i class="bi bi-clock me-1" aria-hidden="true"></i>
            {{ formatDate(productGeneratedAt) }} 업데이트
          </small>
          <p
            v-if="activeAnalysis === 'product' && productRequestMessage"
            class="analysis-request mb-0"
          >
            추천 요청: {{ productRequestMessage }}
          </p>
        </div>

        <div v-if="activeAnalysis === 'asset'" class="analysis-panel" role="tabpanel">
          <AssetAnalysisResult
            v-if="assetResult?.assetAnalysis"
            compact
            :analysis="assetResult.assetAnalysis"
            :show-intro="false"
          />
          <AppState
            v-else
            compact
            type="empty"
            hide-icon
            title="저장된 자산분석이 없습니다."
            message="AI 채팅에서 자산분석을 진행하면 이곳에서 다시 볼 수 있어요."
          >
            <template #actions>
              <AppButton
                class="goal-button pressable"
                size="md"
                @click="startAnalysis('asset-analysis')"
              >자산분석 시작</AppButton>
            </template>
          </AppState>
        </div>

        <div v-else-if="activeAnalysis === 'consumption'" class="analysis-panel" role="tabpanel">
          <AnalysisResult
            v-if="consumptionResult?.consumptionAnalysis"
            compact
            :analysis="consumptionResult.consumptionAnalysis"
          />
          <AppState
            v-else
            compact
            type="empty"
            hide-icon
            title="저장된 소비분석이 없습니다."
            message="AI 채팅에서 소비분석을 진행하면 이곳에서 다시 볼 수 있어요."
          >
            <template #actions>
              <AppButton
                class="goal-button pressable"
                size="md"
                @click="startAnalysis('consumption-analysis')"
              >소비분석 시작</AppButton>
            </template>
          </AppState>
        </div>

        <div v-else class="analysis-panel" role="tabpanel">
          <AppState
            v-if="productStatus === 'loading'"
            compact
            type="loading"
            title="최신 금융상품 추천을 불러오는 중입니다."
          />
          <AppAlert v-else-if="productStatus === 'error'" variant="warning">
            <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
              <span>{{ productError }}</span>
              <AppButton
                variant="outline"
                size="sm"
                @click="productRecommendationStore.fetchLatest({ force: true })"
              >다시 시도</AppButton>
            </div>
          </AppAlert>
          <ProductRecommendationResult
            v-else-if="productStatus === 'success'"
            full-width
            :show-intro="false"
            :recommendation="productRecommendation"
          />
          <AppState
            v-else
            compact
            type="empty"
            hide-icon
            title="저장된 금융상품 추천이 없습니다."
            message="AI 채팅에서 상품 추천을 요청하면 이곳에서 다시 볼 수 있어요."
          >
            <template #actions>
              <AppButton
                class="goal-button pressable"
                size="md"
                @click="startAnalysis('product-recommendation')"
              >
                상품추천 시작
              </AppButton>
            </template>
          </AppState>
        </div>
      </AppCard>
    </div>
  </section>
</template>

<style scoped>
.analysis-dashboard-page {
  padding-bottom: 2rem;
  color: var(--wallo-color-text);
}

.analysis-page-state {
  min-height: 320px;
}

.analysis-section-card {
  overflow: hidden;
  padding: clamp(1rem, 2.5vw, 1.75rem);
  border: 1px solid var(--wallo-color-border);
  border-radius: 1.25rem;
  box-shadow: var(--wallo-shadow-card);
}

.analysis-switcher {
  margin-bottom: var(--wallo-space-4);
}

.analysis-switcher :deep(.app-tabs__list) {
  max-width: 640px;
  margin: 0 auto;
  padding: 0.35rem;
  background: #edf5ff;
  border: 0;
  border-radius: 1rem;
}

.analysis-switcher :deep(.app-tabs__tab) {
  display: inline-flex;
  min-height: 48px;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  border-radius: 0.75rem;
}

.analysis-switcher :deep(.app-tabs__tab--active) {
  color: #fff;
  background: var(--wallo-color-primary);
  box-shadow: 0 6px 16px rgb(79 143 232 / 24%);
}

.analysis-switcher :deep(.app-tabs__tab--active:hover:not(:disabled)) {
  color: #fff;
}

.analysis-panel {
  min-height: 260px;
}

.analysis-panel :deep(.asset-analysis),
.analysis-panel :deep(.consumption-analysis) {
  width: 100%;
  max-width: none;
}

.analysis-meta-bar {
  display: flex;
  min-height: 30px;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding-bottom: 0.75rem;
  margin-bottom: 1.1rem;
  color: var(--wallo-color-text-muted);
}

.analysis-request {
  max-width: 50%;
  color: var(--wallo-color-text-muted);
  font-size: 0.82rem;
  text-align: right;
}

@media (max-width: 767.98px) {
  .analysis-meta-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .analysis-request {
    max-width: none;
    text-align: left;
  }

  .analysis-switcher :deep(.app-tabs__tab) {
    min-width: max-content;
    padding-inline: 0.85rem;
  }
}
</style>
