<script setup>
import { computed, onBeforeUnmount, ref } from "vue"
import { useRouter } from "vue-router"
import { connectAllAssets } from "@/api/assetApi"
import { invalidateConnectionsCache } from "@/api/connectionApi"
import { getLocalInstitutionLogo } from "@/features/asset/institutionLogos"
import { useReportStore } from "@/stores/assetReportStore"
import { useUserStore } from "@/stores/userStore"
import { useAssetStore } from "@/stores/assetStore"
import { getApiErrorMessage } from "@/utils/apiError"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppFormField from "@/components/ui/AppFormField.vue"
import AppDialog from "@/components/common/AppDialog.vue"

const router = useRouter()
const reportStore = useReportStore()
const userStore = useUserStore()
const assetStore = useAssetStore()

const name = ref("")
const phoneNumber = ref("")
const consentAgreed = ref(false)
const isLoading = ref(false)
const isSuccessModalVisible = ref(false)
const progress = ref(0)
const loadingMessage = ref("")
const successMessage = ref("")
const connectedGroups = ref([])
const timers = []

const INSTITUTION_TYPE_LABELS = {
  BANK: "은행",
  CARD: "카드",
  STOCK: "증권",
}

const isFormValid = computed(() =>
  Boolean(name.value.trim() && phoneNumber.value.trim() && consentAgreed.value),
)
const isFormDisabled = computed(() => isLoading.value)
const connectionResultTitle = computed(() =>
  connectedGroups.value.some((group) => group.status !== "SUCCESS") ? "연결 결과" : "연결 완료",
)

const clearTimers = () => {
  timers.forEach((timerId) => clearTimeout(timerId))
  timers.length = 0
}

const wait = (delay) =>
  new Promise((resolve) => {
    const timerId = setTimeout(resolve, delay)
    timers.push(timerId)
  })

const resetProgressState = () => {
  clearTimers()
  progress.value = 0
  loadingMessage.value = ""
}

const getInstitutionName = (result) =>
  result.institutionName || result.name || result.provider || "알 수 없는 기관"

const getLogoText = (institutionName) => {
  if (!institutionName) {
    return "자산"
  }

  return institutionName.replace(/\s/g, "").slice(0, 2)
}

const isSuccessResult = (result) => {
  if (typeof result.connected === "boolean") {
    return result.connected
  }

  if (typeof result.success === "boolean") {
    return result.success
  }

  return String(result.status || "").toUpperCase() === "SUCCESS"
}

const handleLogoError = (event) => {
  const fallbackSrc = event.target.dataset.fallbackSrc
  const currentSrc = event.target.getAttribute("src")

  if (fallbackSrc && currentSrc !== fallbackSrc) {
    event.target.src = fallbackSrc
    return
  }

  event.target.classList.add("d-none")
  event.target.nextElementSibling?.classList.remove("d-none")
}

const getLogoFallbackClass = (logoUrl) => (logoUrl ? "d-none" : "")

const getFailedInstitutions = (results = []) => results.filter((result) => !isSuccessResult(result))

const getInstitutionTypeLabel = (institutionType) =>
  INSTITUTION_TYPE_LABELS[String(institutionType || "").toUpperCase()] || institutionType || "기타"

const groupConnectionResults = (results = []) => {
  const groups = new Map()

  results.forEach((result, index) => {
    const institutionName = getInstitutionName(result)
    const groupCode =
      result.financialGroupCode ||
      `INSTITUTION_${result.institutionId || `${institutionName}_${index}`}`

    if (!groups.has(groupCode)) {
      const groupName = result.financialGroupName || institutionName
      groups.set(groupCode, {
        id: groupCode,
        name: groupName,
        logoText: getLogoText(groupName),
        logoUrl: "",
        localLogoUrl: getLocalInstitutionLogo(groupCode, groupName),
        members: [],
      })
    }

    const group = groups.get(groupCode)
    if (!group.logoUrl && result.logoUrl) {
      group.logoUrl = result.logoUrl
    }
    group.members.push({
      name: institutionName,
      typeLabel: getInstitutionTypeLabel(result.institutionType),
      isSuccess: isSuccessResult(result),
    })
  })

  return Array.from(groups.values()).map((group) => {
    const successCount = group.members.filter((member) => member.isSuccess).length
    const status =
      successCount === group.members.length ? "SUCCESS" : successCount > 0 ? "PARTIAL" : "FAILED"
    const failedNames = group.members
      .filter((member) => !member.isSuccess)
      .map((member) => member.name)

    return {
      ...group,
      typeLabels: [...new Set(group.members.map((member) => member.typeLabel))],
      status,
      message:
        status === "SUCCESS" ? "연동 완료" : status === "PARTIAL" ? "일부 연동 실패" : "연동 실패",
      failedMessage: failedNames.length > 0 ? `${failedNames.join(", ")} 실패` : "",
    }
  })
}

const getResultTextClass = (status) =>
  ({
    SUCCESS: "text-success",
    PARTIAL: "text-warning",
    FAILED: "text-danger",
  })[status] || "text-secondary"

const notifyConnectionResult = (results = []) => {
  const failedInstitutions = getFailedInstitutions(results)

  if (failedInstitutions.length > 0) {
    const failedNames = failedInstitutions.map(getInstitutionName).join(", ")
    console.warn("일부 기관 연동 실패:", failedInstitutions)
    alert(`일부 기관 연동에 실패했습니다. 실패 기관: ${failedNames}`)
  }

  connectedGroups.value = groupConnectionResults(results)
  successMessage.value =
    results.length === 0
      ? "연동된 금융기관이 없습니다."
      : failedInstitutions.length === 0
        ? `총 ${connectedGroups.value.length}개 금융그룹의 자산 연결이 완료되었습니다!`
        : `총 ${connectedGroups.value.length}개 금융그룹의 연동 결과를 확인해 주세요.`
  isSuccessModalVisible.value = true
}

const handleConnectionError = async (error) => {
  const status = error.response?.status
  if (status === 400) {
    alert(getApiErrorMessage(error, "개인신용정보 수집·이용 동의가 필요합니다."))
    return
  }

  if (status === 401) {
    alert("로그인 세션이 만료되었습니다. 다시 로그인해 주세요.")
    await router.push("/login")
    return
  }

  alert(getApiErrorMessage(error, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
}

const handleSubmit = async () => {
  if (!isFormValid.value || isLoading.value) {
    return
  }

  isLoading.value = true
  isSuccessModalVisible.value = false
  successMessage.value = ""
  connectedGroups.value = []
  progress.value = 30
  loadingMessage.value = "금융 기관 보안 연결 중..."

  try {
    await wait(500)
    progress.value = 70
    loadingMessage.value = "계좌 및 카드 데이터를 안전하게 수집하는 중..."

    await wait(700)
    const response = await connectAllAssets(consentAgreed.value)

    progress.value = 100
    loadingMessage.value = "연동 결과를 정리하는 중..."

    const connectionData = response?.data ?? response ?? {}
    reportStore.setAnnualSalaryLookupStatus(connectionData.annualSalaryLookupStatus)
    const results = connectionData.results || []
    invalidateConnectionsCache()
    assetStore.invalidateAssetsCache?.()

    await wait(300)
    notifyConnectionResult(results)
  } catch (error) {
    await handleConnectionError(error)
  } finally {
    isLoading.value = false
    resetProgressState()
  }
}

const moveToDashboard = async () => {
  isSuccessModalVisible.value = false
  try {
    await userStore.restoreSession(true)
    await router.replace("/dashboard")
  } catch (error) {
    alert(error.message || "사용자 연동 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.")
    isSuccessModalVisible.value = true
  }
}

onBeforeUnmount(() => {
  clearTimers()
})
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
          안전한 마이데이터 연결을 통해<br />
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
            고객님의 금융 정보는 금융보안원의 전송 가이드라인에 따라 암호화되어 안전하게 전송 및
            보관됩니다.
          </p>
        </div>
      </div>
    </section>

    <AppCard as="section" class="connection-form-panel" padding="none">
      <div class="connection-form-inner">
        <div class="connection-form-header">
          <h2>자산 연결</h2>
          <p>은행·카드·증권 계정을 연결하여 자산과 거래 내역을 불러옵니다.</p>
        </div>

        <form class="connection-form" @submit.prevent="handleSubmit">
          <fieldset :disabled="isFormDisabled">
            <div class="connection-form-fields">
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
                    모든 은행, 카드, 증권 정보를 한 번에 불러오는 것에 동의하며, 마이데이터 서비스
                    제공을 위해 동의합니다.
                  </small>
                </span>
              </label>

              <AppFormField
                id="userName"
                v-model="name"
                label="이름"
                autocomplete="name"
                placeholder="홍길동"
                class="soft-input"
              />

              <AppFormField
                id="phoneNumber"
                v-model="phoneNumber"
                label="휴대폰 번호"
                type="tel"
                inputmode="tel"
                autocomplete="tel"
                placeholder="01012345678"
                class="soft-input"
              />
            </div>
          </fieldset>

          <AppButton
            type="submit"
            class="connect-action"
            block
            :disabled="!isFormValid || isLoading"
            :loading="isLoading"
          >
            {{ isLoading ? "연결 중..." : "동의하고 자산 연결하기" }}
          </AppButton>
        </form>
      </div>
    </AppCard>

    <div v-if="isLoading" class="connection-progress-backdrop" role="presentation"></div>
    <div
      v-if="isLoading"
      class="connection-progress-layer"
      role="dialog"
      aria-modal="true"
      aria-labelledby="connectionProgressTitle"
    >
      <AppCard as="section" class="connection-progress-card" padding="lg">
        <h2 id="connectionProgressTitle">자산 연결 진행 중</h2>
        <div class="connection-progress-message">
          <span class="connection-progress-spinner" aria-hidden="true"></span>
          <span>{{ loadingMessage }}</span>
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
        <div class="connection-progress-percent">{{ progress }}%</div>
      </AppCard>
    </div>

    <AppDialog
      :visible="isSuccessModalVisible"
      :title="connectionResultTitle"
      :message="successMessage"
      confirm-text="대시보드로 이동"
      :show-cancel="false"
      :show-close="false"
      :close-on-backdrop="false"
      :close-on-esc="false"
      @close="isSuccessModalVisible = false"
      @confirm="moveToDashboard"
    >
      <div class="asset-summary-list">
        <article v-for="group in connectedGroups" :key="group.id" class="asset-summary-item">
          <div class="asset-summary-left">
            <div class="asset-logo">
              <img
                v-if="group.logoUrl || group.localLogoUrl"
                :src="group.logoUrl || group.localLogoUrl"
                :alt="`${group.name} 로고`"
                :data-fallback-src="group.localLogoUrl"
                class="asset-logo-image"
                @error="handleLogoError"
              />
              <span :class="getLogoFallbackClass(group.logoUrl || group.localLogoUrl)">
                {{ group.logoText }}
              </span>
            </div>
            <div class="asset-group-info">
              <strong class="asset-name">{{ group.name }}</strong>
              <div class="d-flex flex-wrap gap-1 mt-1">
                <span
                  v-for="typeLabel in group.typeLabels"
                  :key="typeLabel"
                  class="badge rounded-pill text-bg-light border"
                >
                  {{ typeLabel }}
                </span>
              </div>
            </div>
          </div>
          <div class="asset-result-copy">
            <p :class="['asset-detail mb-0', getResultTextClass(group.status)]">
              {{ group.message }}
            </p>
            <small v-if="group.failedMessage" class="text-secondary">
              {{ group.failedMessage }}
            </small>
          </div>
        </article>
      </div>
    </AppDialog>
  </main>
</template>

<style scoped>
.connection-form fieldset {
  margin: 0;
  padding: 0;
  border: 0;
}

.connection-form {
  display: flex;
  flex-direction: column;
  gap: var(--wallo-space-4);
}

.connection-form-fields {
  display: grid;
  gap: var(--wallo-space-4);
}

.connection-form-fields .consent-card {
  margin-bottom: 0;
}

.connection-progress-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1390;
  background: rgb(19 23 43 / 58%);
}

.connection-progress-layer {
  position: fixed;
  inset: 0;
  z-index: 1391;
  display: grid;
  place-items: center;
  padding: var(--wallo-space-5);
  pointer-events: none;
}

.connection-progress-card {
  width: min(100%, 420px);
  pointer-events: auto;
}

.connection-progress-card h2 {
  margin: 0 0 var(--wallo-space-5);
  font-size: 1.2rem;
}

.connection-progress-message {
  display: flex;
  align-items: center;
  gap: var(--wallo-space-2);
  margin-bottom: var(--wallo-space-3);
  font-weight: 700;
}

.connection-progress-spinner {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
  border: 2px solid var(--wallo-color-primary);
  border-right-color: transparent;
  border-radius: 50%;
  animation: connection-progress-spin 700ms linear infinite;
}

.connection-progress-percent {
  margin-top: var(--wallo-space-2);
  color: var(--wallo-color-text-muted);
  font-size: 0.875rem;
  text-align: right;
}

.asset-group-info {
  min-width: 0;
}

.asset-result-copy {
  flex: 0 0 auto;
  text-align: right;
}

.asset-result-copy small {
  display: block;
  margin-top: 2px;
}

@media (max-width: 576px) {
  .asset-summary-item {
    align-items: flex-start;
  }
}

@keyframes connection-progress-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .connection-progress-spinner {
    animation: none;
  }
}
</style>
