<script setup>
import { onMounted } from "vue";
import { storeToRefs } from "pinia";
import AssetOverviewCard from "@/components/asset/AssetOverviewCard.vue";
import ConsumptionReportCard from "@/components/asset/ConsumptionReportCard.vue";
import TaxDeductionTrackerCard from "@/components/asset/TaxDeductionTrackerCard.vue";
import { useAssetStore } from "@/stores/assetStore";

const assetStore = useAssetStore();
const { assets, error, isAssetLoading } = storeToRefs(assetStore);

const loadAssets = async () => {
  try {
    await assetStore.fetchAssets();
  } catch {
    // 오류 메시지와 401 이동은 Pinia 및 Axios 인터셉터에서 처리합니다.
  }
};

onMounted(loadAssets);
</script>

<template>
  <section class="asset-view container-fluid px-4 py-4">
    <header class="mb-4">
      <div>
        <h1 class="h3 fw-bold mb-1">자산관리</h1>
        <p class="text-secondary mb-0">
          연결된 계좌와 투자 자산을 한곳에서 확인하세요.
        </p>
      </div>
    </header>

    <section class="asset-overview-section" aria-label="자산 현황">
      <div v-if="isAssetLoading" class="asset-state" aria-live="polite">
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">자산 정보를 불러오는 중</span>
        </div>
        <p class="text-secondary mb-0 mt-3">자산 정보를 불러오고 있습니다.</p>
      </div>

      <div v-else-if="error" class="alert alert-danger asset-error" role="alert">
        <div>
          <h2 class="h6 fw-bold mb-1">자산 정보를 불러오지 못했습니다.</h2>
          <p class="mb-0">{{ error }}</p>
        </div>
        <button type="button" class="btn btn-outline-danger flex-shrink-0" @click="loadAssets">
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

    <section class="row g-4 mt-0 asset-report-grid" aria-label="자산 리포트">
      <div class="col-12 col-lg-6">
        <ConsumptionReportCard />
      </div>

      <div class="col-12 col-lg-6">
        <TaxDeductionTrackerCard />
      </div>
    </section>
  </section>
</template>

<style scoped>
.asset-view {
  width: 100%;
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
