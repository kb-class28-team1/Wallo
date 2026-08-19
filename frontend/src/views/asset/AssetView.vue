<script setup>
import { computed, onMounted, ref } from "vue"
import { storeToRefs } from "pinia"
import AssetOverviewCard from "@/components/asset/AssetOverviewCard.vue"
import ConsumptionReportCard from "@/components/asset/ConsumptionReportCard.vue"
import TaxDeductionTrackerCard from "@/components/asset/TaxDeductionTrackerCard.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"
import { useAssetStore } from "@/stores/assetStore"

const assetStore = useAssetStore()
const {
  assets,
  error,
  initialLoading: assetInitialLoading,
  refreshing: assetRefreshing,
  isAssetLoading,
  isSyncing,
  syncError,
} = storeToRefs(assetStore)
const reportRefreshKey = ref(0)
const syncStatus = ref(null)

const isInitialLoading = computed(
  () => assetInitialLoading?.value ?? Boolean(isAssetLoading?.value && !assets.value),
)
const isRefreshing = computed(() => assetRefreshing?.value ?? false)

const loadAssets = async ({ force = false } = {}) => {
  try {
    await assetStore.fetchAssets({ force })
  } catch {
    // 오류 메시지와 401 이동은 Pinia 및 Axios 인터셉터에서 처리합니다.
  }
}

const syncAssets = async () => {
  syncStatus.value = null

  try {
    const result = await assetStore.syncAssets()
    if (!result) return

    await assetStore.fetchAssets({ notifyError: false })
    reportRefreshKey.value += 1

    const failedConnections = Number(result.failedConnections) || 0
    const fallbackCount = Number(result.fallbackCount) || 0
    const summary = `신규 ${Number(result.inserted) || 0}건, 수정 ${Number(result.updated) || 0}건`
    const warnings = []
    if (failedConnections > 0) {
      warnings.push(`실패한 연결기관 ${failedConnections}건`)
    }
    if (fallbackCount > 0) {
      warnings.push(`AI 분류 실패로 기타 처리된 거래 ${fallbackCount}건`)
    }
    syncStatus.value = warnings.length > 0
      ? {
          type: "warning",
          message: `동기화가 완료되었습니다. ${summary}. ${warnings.join(", ")}`,
        }
      : {
          type: "success",
          message: `동기화가 완료되었습니다. ${summary}`,
        }
  } catch {
    syncStatus.value = {
      type: "danger",
      message: syncError.value || "자산 거래내역 동기화에 실패했습니다.",
    }
  }
}

onMounted(loadAssets)
</script>

<template>
  <section class="asset-view">
    <AppPageHeader title="자산관리">
      <template #actions>
        <AppButton
          class="asset-sync-button"
          variant="primary"
          type="button"
          :disabled="isSyncing || isInitialLoading || isRefreshing"
          @click="syncAssets"
        >
          <span
            v-if="isSyncing"
            class="spinner-border spinner-border-sm me-2"
            aria-hidden="true"
          ></span>
          {{ isSyncing ? "동기화 중..." : "거래내역 새로고침" }}
        </AppButton>
      </template>
    </AppPageHeader>

    <AppAlert
      v-if="syncStatus"
      class="asset-sync-status"
      :variant="syncStatus.type"
      :message="syncStatus.message"
      role="status"
    />

    <div v-if="isRefreshing" class="asset-refresh-status" role="status">
      <span class="spinner-border spinner-border-sm text-primary me-2" aria-hidden="true"></span>
      자산 정보를 최신 상태로 갱신하고 있습니다.
    </div>

    <section class="asset-overview-section" aria-label="자산 현황">
      <AppState
        v-if="isInitialLoading"
        class="asset-state"
        type="loading"
        title="자산 정보를 불러오는 중입니다."
        message="잠시만 기다려 주세요."
      />

      <AppAlert v-else-if="error && !assets" class="asset-error" variant="danger">
        <div>
          <strong class="d-block mb-1">자산 정보를 불러오지 못했습니다.</strong>
          <span>{{ error }}</span>
        </div>
        <AppButton variant="outline" size="sm" @click="loadAssets({ force: true })">
          다시 시도
        </AppButton>
      </AppAlert>

      <AssetOverviewCard v-else-if="assets" :assets="assets" />

      <AppState v-else class="asset-state" type="empty" title="연결된 자산이 없습니다.">
        <template #actions>
          <RouterLink to="/users/profile/connections" class="asset-connect-link">
            연동관리로 이동
          </RouterLink>
        </template>
        금융기관을 연동하면 자산 현황을 확인할 수 있습니다.
      </AppState>
    </section>

    <AppAlert
      v-if="error && assets"
      class="asset-refresh-alert"
      variant="warning"
      message="최신 자산 정보를 갱신하지 못했습니다. 기존 정보를 표시하고 있습니다."
    >
      <span>최신 자산 정보를 갱신하지 못했습니다. 기존 정보를 표시하고 있습니다.</span>
      <AppButton variant="outline" size="sm" @click="loadAssets({ force: true })">
        다시 시도
      </AppButton>
    </AppAlert>

    <section class="asset-report-grid" aria-label="자산 리포트">
      <div>
        <ConsumptionReportCard
          :key="`consumption-report-${reportRefreshKey}`"
          :force-refresh="reportRefreshKey > 0"
        />
      </div>

      <div>
        <TaxDeductionTrackerCard
          :key="`tax-deduction-${reportRefreshKey}`"
          :force-refresh="reportRefreshKey > 0"
        />
      </div>
    </section>
  </section>
</template>

<style scoped>
.asset-view {
  width: 100%;
  padding: var(--wallo-space-6) var(--wallo-space-4);
}

.asset-sync-button {
  min-width: 172px;
}

.asset-sync-status,
.asset-refresh-alert {
  margin-bottom: var(--wallo-space-4);
}

.asset-refresh-status {
  display: inline-flex;
  align-items: center;
  margin-bottom: var(--wallo-space-4);
  color: var(--wallo-color-text-muted);
  font-size: 0.875rem;
}

.asset-overview-section {
  width: 100%;
}

.asset-report-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--wallo-space-6);
  margin-top: var(--wallo-space-6);
}

.asset-state {
  min-height: 360px;
}

.asset-connect-link {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  padding: 0 var(--wallo-space-3);
  color: var(--wallo-color-surface);
  background: var(--wallo-color-primary);
  border-radius: var(--wallo-radius-sm);
  font-weight: 700;
  text-decoration: none;
}

.asset-error {
  min-height: 110px;
  align-items: center;
  justify-content: space-between;
}

@media (max-width: 575.98px) {
  .asset-view {
    padding-right: var(--wallo-space-3);
    padding-left: var(--wallo-space-3);
  }

  .asset-error {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 991.98px) {
  .asset-report-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--wallo-space-5);
    margin-top: var(--wallo-space-5);
  }
}
</style>
