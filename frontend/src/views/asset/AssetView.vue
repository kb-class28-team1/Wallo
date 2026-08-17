<script setup>
import { computed, onMounted, ref } from "vue";
import { storeToRefs } from "pinia";
import AssetOverviewCard from "@/components/asset/AssetOverviewCard.vue";
import ConsumptionReportCard from "@/components/asset/ConsumptionReportCard.vue";
import TaxDeductionTrackerCard from "@/components/asset/TaxDeductionTrackerCard.vue";
import { useAssetStore } from "@/stores/assetStore";

const assetStore = useAssetStore();
const {
  assets,
  error,
  initialLoading: assetInitialLoading,
  refreshing: assetRefreshing,
  isAssetLoading,
  isSyncing,
  syncError,
} = storeToRefs(assetStore);
const reportRefreshKey = ref(0);
const syncStatus = ref(null);

const isInitialLoading = computed(() => (
  assetInitialLoading?.value ?? Boolean(isAssetLoading?.value && !assets.value)
));
const isRefreshing = computed(() => assetRefreshing?.value ?? false);

const loadAssets = async ({ force = false } = {}) => {
  try {
    await assetStore.fetchAssets({ force });
  } catch {
    // 오류 메시지와 401 이동은 Pinia 및 Axios 인터셉터에서 처리합니다.
  }
};

const syncAssets = async () => {
  syncStatus.value = null;

  try {
    const result = await assetStore.syncAssets();
    if (!result) return;

    await assetStore.fetchAssets({ notifyError: false });
    reportRefreshKey.value += 1;

    const failedConnections = Number(result.failedConnections) || 0;
    const fallbackCount = Number(result.fallbackCount) || 0;
    const summary = `신규 ${Number(result.inserted) || 0}건, 수정 ${Number(result.updated) || 0}건`;
    const warnings = [];
    if (failedConnections > 0) {
      warnings.push(`실패한 연결기관 ${failedConnections}건`);
    }
    if (fallbackCount > 0) {
      warnings.push(`AI 분류 실패로 기타 처리된 거래 ${fallbackCount}건`);
    }
    syncStatus.value = warnings.length > 0
      ? {
          type: "warning",
          message: `동기화가 완료되었습니다. ${summary}. ${warnings.join(", ")}`,
        }
      : {
          type: "success",
          message: `동기화가 완료되었습니다. ${summary}`,
        };
  } catch {
    syncStatus.value = {
      type: "danger",
      message: syncError.value || "자산 거래내역 동기화에 실패했습니다.",
    };
  }
};

onMounted(loadAssets);
</script>

<template>
  <section class="asset-view container-fluid px-4 py-4">
    <header class="asset-page-header d-flex flex-wrap align-items-start justify-content-between gap-3 mb-4">
      <div>
        <h1 class="h3 fw-bold mb-1">자산관리</h1>
        <p class="text-secondary mb-0">
          연결된 계좌와 투자 자산을 한곳에서 확인하세요.
        </p>
      </div>
      <button
        type="button"
        class="btn btn-primary asset-sync-button"
        :disabled="isSyncing || isInitialLoading || isRefreshing"
        @click="syncAssets"
      >
        <span
          v-if="isSyncing"
          class="spinner-border spinner-border-sm me-2"
          aria-hidden="true"
        ></span>
        {{ isSyncing ? "동기화 중..." : "거래내역 새로고침" }}
      </button>
    </header>

    <div
      v-if="syncStatus"
      class="alert"
      :class="`alert-${syncStatus.type}`"
      role="status"
    >
      {{ syncStatus.message }}
    </div>

    <div v-if="isRefreshing" class="small text-secondary mb-3" role="status">
      <span class="spinner-border spinner-border-sm text-primary me-2" aria-hidden="true"></span>
      자산 정보를 최신 상태로 갱신하고 있습니다.
    </div>

    <section class="asset-overview-section" aria-label="자산 현황">
      <div v-if="isInitialLoading" class="asset-state" aria-live="polite">
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">자산 정보를 불러오는 중</span>
        </div>
        <p class="text-secondary mb-0 mt-3">자산 정보를 불러오고 있습니다.</p>
      </div>

      <div v-else-if="error && !assets" class="alert alert-danger asset-error" role="alert">
        <div>
          <h2 class="h6 fw-bold mb-1">자산 정보를 불러오지 못했습니다.</h2>
          <p class="mb-0">{{ error }}</p>
        </div>
        <button type="button" class="btn btn-outline-danger flex-shrink-0" @click="loadAssets({ force: true })">
          다시 시도
        </button>
      </div>

      <AssetOverviewCard v-else-if="assets" :assets="assets" />

      <div v-else class="asset-state">
        <i class="bi bi-wallet2 fs-1 text-secondary" aria-hidden="true"></i>
        <h2 class="h5 fw-bold mb-1 mt-3">연결된 자산이 없습니다.</h2>
        <p class="text-secondary mb-3">
          금융기관을 연동하면 자산 현황을 확인할 수 있습니다.
        </p>
        <RouterLink to="/users/profile/connections" class="btn btn-primary">
          연동관리로 이동
        </RouterLink>
      </div>
    </section>

    <div v-if="error && assets" class="alert alert-warning mt-3" role="alert">
      최신 자산 정보를 갱신하지 못했습니다. 기존 정보를 표시하고 있습니다.
      <button type="button" class="btn btn-sm btn-outline-warning ms-2" @click="loadAssets({ force: true })">
        다시 시도
      </button>
    </div>

    <section class="row g-4 mt-0 asset-report-grid" aria-label="자산 리포트">
      <div class="col-12 col-lg-6">
        <ConsumptionReportCard
          :key="`consumption-report-${reportRefreshKey}`"
          :force-refresh="reportRefreshKey > 0"
        />
      </div>

      <div class="col-12 col-lg-6">
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
}

.asset-sync-button {
  min-width: 172px;
}

.asset-overview-section {
  width: 100%;
}

.asset-report-grid {
  padding-top: 24px;
}

.asset-state {
  display: flex;
  min-height: 360px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 28px;
  background: #ffffff;
  box-shadow: 0 0.25rem 1rem rgba(35, 31, 67, 0.06);
  text-align: center;
}

.asset-error {
  display: flex;
  min-height: 110px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-radius: 18px;
}

@media (max-width: 575.98px) {
  .asset-view {
    padding-right: 0 !important;
    padding-left: 0 !important;
  }

  .asset-error {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
