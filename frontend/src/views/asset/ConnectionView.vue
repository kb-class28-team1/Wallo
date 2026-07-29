<script setup>
import { computed, onBeforeUnmount, ref } from "vue";
import { useRouter } from "vue-router";
import { connectAllAssets } from "@/api/assetApi";
import { useAssetStore } from "@/stores/assetStore";

const router = useRouter();
const assetStore = useAssetStore();

const name = ref("");
const phoneNumber = ref("");
const consentAgreed = ref(false);
const isLoading = ref(false);
const isSuccessModalVisible = ref(false);
const progress = ref(0);
const loadingMessage = ref("");
const successMessage = ref("");
const connectedAssets = ref([]);
const timers = [];

const isFormValid = computed(() => Boolean(
  name.value.trim() && phoneNumber.value.trim() && consentAgreed.value,
));
const isFormDisabled = computed(() => isLoading.value);

const clearTimers = () => {
  timers.forEach((timerId) => clearTimeout(timerId));
  timers.length = 0;
};

const wait = (delay) => new Promise((resolve) => {
  const timerId = setTimeout(resolve, delay);
  timers.push(timerId);
});

const resetProgressState = () => {
  clearTimers();
  progress.value = 0;
  loadingMessage.value = "";
};

const getInstitutionName = (result) => (
  result.institutionName || result.name || result.provider || "알 수 없는 기관"
);

const getLogoText = (institutionName) => {
  if (!institutionName) {
    return "자산";
  }

  return institutionName.replace(/\s/g, "").slice(0, 2);
};

const isSuccessResult = (result) => String(result.status || "").toUpperCase() === "SUCCESS";

const handleLogoError = (event) => {
  event.target.classList.add("d-none");
  event.target.nextElementSibling?.classList.remove("d-none");
};

const getFailedInstitutions = (results = []) => results.filter((result) => {
  if (typeof result.connected === "boolean") {
    return !result.connected;
  }

  if (typeof result.success === "boolean") {
    return !result.success;
  }

  const status = String(result.status || "").toUpperCase();
  return status === "FAILED" || status === "FAIL";
});

const notifyConnectionResult = (results = []) => {
  const failedInstitutions = getFailedInstitutions(results);

  if (failedInstitutions.length > 0) {
    const failedNames = failedInstitutions.map(getInstitutionName).join(", ");
    console.warn("일부 기관 연동 실패:", failedInstitutions);
    alert(`일부 기관 연동에 실패했습니다. 실패 기관: ${failedNames}`);
  }

  const successCount = results.filter(isSuccessResult).length;
  successMessage.value = `총 ${successCount}개 기관의 자산 연동이 완료되었습니다!`;
  connectedAssets.value = results.map((result, index) => {
    const institutionName = getInstitutionName(result);

    return {
      id: result.institutionId || `${institutionName}-${index}`,
      logoText: getLogoText(institutionName),
      logoUrl: result.logoUrl || "",
      name: institutionName,
      message: result.message || (isSuccessResult(result) ? "연동 완료" : "연동 실패"),
    };
  });
  isSuccessModalVisible.value = true;
};

const handleConnectionError = async (error) => {
  const status = error.response?.status;
  const serverMessage = error.response?.data?.error?.message;

  if (status === 400) {
    alert(serverMessage || "개인신용정보 수집·이용 동의가 필요합니다.");
    return;
  }

  if (status === 401) {
    alert("로그인 세션이 만료되었습니다. 다시 로그인해 주세요.");
    await router.push("/login");
    return;
  }

  alert(serverMessage || "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
};

const handleSubmit = async () => {
  if (!isFormValid.value || isLoading.value) {
    return;
  }

  isLoading.value = true;
  isSuccessModalVisible.value = false;
  successMessage.value = "";
  connectedAssets.value = [];
  progress.value = 30;
  loadingMessage.value = "금융 기관 보안 연결 중...";

  try {
    await wait(500);
    progress.value = 70;
    loadingMessage.value = "계좌 및 카드 데이터를 안전하게 수집하는 중...";

    await wait(700);
    const response = await connectAllAssets(true);

    progress.value = 100;
    loadingMessage.value = "연동 결과를 정리하는 중...";

    const results = response?.data?.results || response?.results || [];
    assetStore.connectionResults = results;

    await wait(300);
    notifyConnectionResult(results);
  } catch (error) {
    await handleConnectionError(error);
  } finally {
    isLoading.value = false;
    resetProgressState();
  }
};

const moveToDashboard = async () => {
  isSuccessModalVisible.value = false;
  await router.push("/dashboard");
};

onBeforeUnmount(() => {
  clearTimers();
});
</script>

<template>
  <main class="connection-start-page">
    <section class="connection-hero-panel">
      <div>
        <h1 class="connection-hero-title">
          흩어져 있던 금융 정보,<br />
          <span>한 번에 모아볼까요?</span>
        </h1>
        <p class="connection-hero-copy">
          안전한 마이데이터 연동을 통해<br />
          모든 계좌와 카드 내역을 한곳에서 관리하세요.
        </p>
      </div>

      <div class="security-note">
        <div>
          <div class="security-note-title">
            <i class="bi bi-shield-check security-icon" aria-hidden="true"></i>
            <strong>안전하게 보호합니다</strong>
          </div>
          <p class="mb-0">
            고객님의 금융 정보는 금융보안원의 전송 가이드라인에 따라 암호화되어 안전하게 전송 및 보관됩니다.
          </p>
        </div>
      </div>
    </section>

    <section class="connection-form-panel">
      <div class="connection-form-inner">
        <div class="connection-form-header">
          <h2>연동 시작하기</h2>
          <p>서비스 이용을 위한 통합 동의 및 본인 확인 단계입니다.</p>
        </div>

        <form class="connection-form" @submit.prevent="handleSubmit">
          <fieldset :disabled="isFormDisabled">
            <label class="consent-card" for="creditConsent">
              <input
                id="creditConsent"
                v-model="consentAgreed"
                class="form-check-input consent-check"
                type="checkbox"
              />
              <span>
                <strong>[필수] 통합 자산 정보 수집·이용 동의</strong>
                <small>
                  모든 은행, 카드, 증권 정보를 한 번에 불러오는 것에 동의하며, 마이데이터 서비스 제공을 위해 동의합니다.
                </small>
              </span>
            </label>

            <div class="mb-3">
              <label for="userName" class="form-label">이름</label>
              <input
                id="userName"
                v-model="name"
                type="text"
                class="form-control soft-input"
                autocomplete="name"
                placeholder="홍길동"
              />
            </div>

            <div class="mb-4">
              <label for="phoneNumber" class="form-label">휴대폰 번호</label>
              <input
                id="phoneNumber"
                v-model="phoneNumber"
                type="tel"
                class="form-control soft-input"
                inputmode="tel"
                autocomplete="tel"
                placeholder="01012345678"
              />
            </div>
          </fieldset>

          <button
            type="submit"
            class="btn w-100 connect-action"
            :disabled="!isFormValid || isLoading"
          >
            <span
              v-if="isLoading"
              class="spinner-border spinner-border-sm me-2"
              aria-hidden="true"
            ></span>
            <span>{{ isLoading ? "연동 중..." : "동의하고 전체 연동하기" }}</span>
          </button>
        </form>
      </div>
    </section>

    <div v-if="isLoading" class="modal-backdrop fade show"></div>
    <div
      v-if="isLoading"
      class="modal fade show d-block"
      tabindex="-1"
      role="dialog"
      aria-modal="true"
      aria-labelledby="connectionProgressTitle"
    >
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content app-modal">
          <div class="modal-header">
            <h2 id="connectionProgressTitle" class="modal-title h5">자산 연동 진행 중</h2>
          </div>
          <div class="modal-body">
            <div class="d-flex align-items-center mb-3">
              <span
                class="spinner-border spinner-border-sm text-primary me-2"
                aria-hidden="true"
              ></span>
              <span class="fw-semibold">{{ loadingMessage }}</span>
            </div>
            <div
              class="progress"
              role="progressbar"
              :aria-valuenow="progress"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <div
                class="progress-bar progress-bar-striped progress-bar-animated"
                :style="{ width: `${progress}%` }"
              ></div>
            </div>
            <div class="text-end small text-secondary mt-2">{{ progress }}%</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="isSuccessModalVisible" class="modal-backdrop fade show"></div>
    <div
      v-if="isSuccessModalVisible"
      class="modal fade show d-block"
      tabindex="-1"
      role="dialog"
      aria-modal="true"
      aria-labelledby="connectionSuccessTitle"
    >
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content app-modal">
          <div class="modal-header">
            <h2 id="connectionSuccessTitle" class="modal-title h5">연동 완료</h2>
          </div>
          <div class="modal-body">
            <p class="modal-message mb-3">{{ successMessage }}</p>
            <div class="asset-summary-list">
              <article
                v-for="asset in connectedAssets"
                :key="asset.id"
                class="asset-summary-item"
              >
                <div class="asset-summary-left">
                  <div class="asset-logo">
                    <img
                      v-if="asset.logoUrl"
                      :src="asset.logoUrl"
                      :alt="`${asset.name} 로고`"
                      class="asset-logo-image"
                      @error="handleLogoError"
                    />
                    <span :class="asset.logoUrl ? 'd-none' : ''">
                      {{ asset.logoText }}
                    </span>
                  </div>
                  <div>
                    <strong class="asset-name">{{ asset.name }}</strong>
                  </div>
                </div>
                <p class="asset-detail mb-0">{{ asset.message }}</p>
              </article>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-primary" @click="moveToDashboard">
              대시보드로 이동
            </button>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.connection-form fieldset {
  margin: 0;
  padding: 0;
  border: 0;
}
</style>
