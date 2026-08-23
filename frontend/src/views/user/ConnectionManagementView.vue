<script setup>
import { computed, nextTick, onMounted, ref } from "vue"
import { RouterLink } from "vue-router"
import { getApiErrorMessage } from "@/utils/apiError"
import { disconnectConnection, getConnections } from "@/api/connectionApi"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppState from "@/components/ui/AppState.vue"
import AppTabs from "@/components/ui/AppTabs.vue"
import { getLocalInstitutionLogo } from "@/features/asset/institutionLogos"
import { useAssetStore } from "@/stores/assetStore"

const assetStore = useAssetStore()
const connections = ref([])
const initialLoading = ref(true)
const refreshing = ref(false)
const hasLoadedConnections = ref(false)
const errorMessage = ref("")
const successMessage = ref("")
const disconnectingId = ref(null)
const pendingDisconnectConnection = ref(null)
const disconnectModalError = ref("")
const disconnectModalRef = ref(null)
const previousFocusedElement = ref(null)
const connectionCategories = Object.freeze([
  { key: "ACCOUNT", value: "ACCOUNT", label: "계좌", emptyMessage: "연결된 계좌가 없습니다." },
  { key: "CARD", value: "CARD", label: "카드", emptyMessage: "연결된 카드가 없습니다." },
  { key: "STOCK", value: "STOCK", label: "증권", emptyMessage: "연결된 증권이 없습니다." },
])
const activeCategory = ref("ACCOUNT")

const getConnectionCategory = (connection) => {
  if (connection.assetKind === "CARD") {
    return "CARD"
  }

  return connection.assetType === "STOCK" ? "STOCK" : "ACCOUNT"
}

const connectionGroups = computed(() => {
  const groupedConnections = new Map()

  connections.value.forEach((asset) => {
    const groupKey =
      asset.connectionId ??
      `institution-${asset.institutionId ?? "unknown"}-${asset.institutionName ?? "unknown"}`
    const existingGroup = groupedConnections.get(groupKey)

    if (existingGroup) {
      existingGroup.assets.push(asset)
      if (!existingGroup.lastSyncAt && asset.lastSyncAt) {
        existingGroup.lastSyncAt = asset.lastSyncAt
      }
      return
    }

    groupedConnections.set(groupKey, {
      groupKey,
      connectionId: asset.connectionId,
      institutionId: asset.institutionId,
      institutionName: asset.institutionName,
      institutionType: asset.institutionType,
      logoUrl: asset.logoUrl,
      financialGroupCode: asset.financialGroupCode,
      financialGroupName: asset.financialGroupName,
      lastSyncAt: asset.lastSyncAt,
      assets: [asset],
    })
  })

  return [...groupedConnections.values()]
})

const categorizedConnections = computed(() => {
  const groupedConnections = {
    ACCOUNT: [],
    CARD: [],
    STOCK: [],
  }

  connectionGroups.value.forEach((group) => {
    Object.keys(groupedConnections).forEach((categoryKey) => {
      const visibleAssets = group.assets.filter(
        (asset) => getConnectionCategory(asset) === categoryKey,
      )

      if (visibleAssets.length > 0) {
        groupedConnections[categoryKey].push({
          ...group,
          visibleAssets,
        })
      }
    })
  })

  return groupedConnections
})

const visibleConnections = computed(() => categorizedConnections.value[activeCategory.value] || [])

const activeCategoryLabel = computed(
  () => connectionCategories.find(({ key }) => key === activeCategory.value)?.label || "계좌",
)

const activeCategoryEmptyMessage = computed(
  () =>
    connectionCategories.find(({ key }) => key === activeCategory.value)?.emptyMessage ||
    "연결된 계좌가 없습니다.",
)

const getCategoryCount = (categoryKey) => categorizedConnections.value[categoryKey]?.length || 0

const activeCategoryAssetCount = computed(() =>
  visibleConnections.value.reduce((total, group) => total + group.visibleAssets.length, 0),
)

const formatAmount = (amount, currency = "KRW") => {
  const normalizedCurrency = currency || "KRW"
  const amountText = new Intl.NumberFormat("ko-KR").format(Number(amount) || 0)
  const currencyUnit = {
    KRW: "원",
    USD: "달러",
    JPY: "엔",
    EUR: "유로",
  }[normalizedCurrency]

  return currencyUnit ? `${amountText}${currencyUnit}` : `${amountText} ${normalizedCurrency}`
}

const formatLastSync = (lastSyncAt) => {
  if (!lastSyncAt) {
    return "최근 동기화 정보 없음"
  }

  const date = new Date(lastSyncAt)
  if (Number.isNaN(date.getTime())) {
    return "최근 동기화 정보 없음"
  }

  return `마지막 동기화 ${new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date)}`
}

const getAssetTypeLabel = (connection) => {
  if (connection.assetKind === "CARD") {
    if (connection.assetType === "CHECK") {
      return "체크카드"
    }

    if (connection.assetType === "CREDIT") {
      return "신용카드"
    }

    return "카드 유형 확인 필요"
  }

  return (
    {
      BANK: "입출금",
      STOCK: "투자계좌",
      LOAN: "대출",
    }[connection.assetType] || "계좌"
  )
}

const getLogoText = (connection) => {
  const name = connection.institutionName || "금융"
  return name.replace(/\s/g, "").slice(0, 2)
}

const getLocalConnectionLogoUrl = (connection) =>
  getLocalInstitutionLogo(
    connection.financialGroupCode,
    connection.financialGroupName || connection.institutionName,
  )

const getConnectionLogoUrl = (connection) => {
  const localLogoUrl = getLocalConnectionLogoUrl(connection)

  if (connection.financialGroupCode?.toUpperCase() === "KB" && localLogoUrl) {
    return localLogoUrl
  }

  return connection.logoUrl || localLogoUrl
}

const getConnectionFallbackLogoUrl = (connection) =>
  connection.logoUrl
    ? getLocalConnectionLogoUrl(connection)
    : ""

const getLogoFallbackClass = (logoUrl) => (logoUrl ? "d-none" : "")

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

const loadConnections = async ({ force = false } = {}) => {
  const isInitialLoad = !hasLoadedConnections.value
  initialLoading.value = isInitialLoad
  refreshing.value = !isInitialLoad
  errorMessage.value = ""

  try {
    const response = await getConnections({ force })
    connections.value = Array.isArray(response?.connections) ? response.connections : []
    hasLoadedConnections.value = true
  } catch (error) {
    if (isInitialLoad) {
      connections.value = []
      hasLoadedConnections.value = false
    }
    errorMessage.value = getApiErrorMessage(error, "연결된 자산 정보를 불러오지 못했습니다.")
  } finally {
    initialLoading.value = false
    refreshing.value = false
  }
}

const openDisconnectModal = (connection) => {
  previousFocusedElement.value = document.activeElement
  pendingDisconnectConnection.value = connection
  disconnectModalError.value = ""

  nextTick(() => disconnectModalRef.value?.focus())
}

const restoreModalFocus = () => {
  const elementToFocus = previousFocusedElement.value
  previousFocusedElement.value = null

  nextTick(() => {
    const fallbackElement = document.querySelector(
      ".connection-category-tabs [role='tab'][aria-selected='true']",
    )
    const isFocusableTarget = (element) =>
      element &&
      element !== document.body &&
      element.isConnected &&
      typeof element.focus === "function"
    const focusTarget = isFocusableTarget(elementToFocus)
      ? elementToFocus
      : isFocusableTarget(fallbackElement)
        ? fallbackElement
        : null

    focusTarget?.focus?.()
  })
}

const closeDisconnectModal = () => {
  if (disconnectingId.value !== null) {
    return
  }

  pendingDisconnectConnection.value = null
  disconnectModalError.value = ""
  restoreModalFocus()
}

const handleModalKeydown = (event) => {
  if (event.key === "Escape") {
    closeDisconnectModal()
    return
  }

  if (event.key !== "Tab" || !disconnectModalRef.value) {
    return
  }

  const focusableElements = [
    ...disconnectModalRef.value.querySelectorAll(
      "button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])",
    ),
  ]

  if (focusableElements.length === 0) {
    event.preventDefault()
    return
  }

  const firstElement = focusableElements[0]
  const lastElement = focusableElements[focusableElements.length - 1]

  if (event.shiftKey && document.activeElement === firstElement) {
    event.preventDefault()
    lastElement.focus()
  } else if (!event.shiftKey && document.activeElement === lastElement) {
    event.preventDefault()
    firstElement.focus()
  }
}

const handleDisconnect = async () => {
  const connection = pendingDisconnectConnection.value
  if (!connection || disconnectingId.value !== null) {
    return
  }

  const institutionName = connection.institutionName || "선택한 금융기관"
  disconnectingId.value = connection.connectionId
  errorMessage.value = ""
  successMessage.value = ""
  disconnectModalError.value = ""

  try {
    await disconnectConnection(connection.connectionId)
    assetStore.invalidateAssetsCache?.()
    connections.value = connections.value.filter(
      (item) => item.connectionId !== connection.connectionId,
    )
    pendingDisconnectConnection.value = null
    restoreModalFocus()

    try {
      await assetStore.fetchAssets({ notifyError: false })
    } catch (refreshError) {
      errorMessage.value = getApiErrorMessage(
        refreshError,
        "연결은 해제되었지만 자산 요약을 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      )
    }

    successMessage.value = `${institutionName} 연결이 해제되었습니다.`
  } catch (error) {
    disconnectModalError.value = getApiErrorMessage(error, "자산 연결을 해제하지 못했습니다.")
  } finally {
    disconnectingId.value = null
  }
}

onMounted(loadConnections)
</script>

<template>
  <div class="connection-management-view">
    <AppCard
      as="section"
      class="settings-panel"
      padding="none"
      aria-labelledby="connection-settings-title"
    >
      <div v-if="refreshing" class="connection-refresh-status" role="status">
        최신 연결 정보를 확인하는 중...
      </div>

    <AppState
      v-if="initialLoading"
      class="connection-state"
      type="loading"
      title="연결된 자산 정보를 불러오는 중입니다."
      message="잠시만 기다려 주세요."
      compact
    />

      <div v-else class="connection-card-body">
        <h2 id="connection-settings-title" class="h5 fw-bold mb-4">연결된 자산</h2>

        <AppAlert v-if="errorMessage" class="connection-alert" variant="danger" :show-icon="false">
          <div class="d-flex align-items-center justify-content-between gap-3">
            <span>{{ errorMessage }}</span>
            <AppButton variant="outline" size="sm" @click="loadConnections({ force: true })">
              다시 시도
            </AppButton>
          </div>
        </AppAlert>

        <AppAlert
          v-if="successMessage"
          class="connection-alert"
          variant="success"
          :message="successMessage"
          :show-icon="false"
          role="status"
        />

        <AppTabs
          v-model="activeCategory"
          class="connection-category-tabs"
          :items="connectionCategories"
          variant="segment"
          full-width
          aria-label="연결된 자산 유형"
        >
          <template #tab="{ item }">
            {{ item.label }}
            <span class="connection-category-count">{{ getCategoryCount(item.value) }}</span>
          </template>
        </AppTabs>

        <div
          class="connection-section-heading d-flex align-items-center justify-content-between gap-3"
        >
          <span class="small text-secondary">
            {{ activeCategoryLabel }} 연결 기관 {{ visibleConnections.length }}곳 · 자산
            {{ activeCategoryAssetCount }}개
          </span>
        </div>

        <div
          v-if="visibleConnections.length > 0"
          :id="`connection-category-panel-${activeCategory.toLowerCase()}`"
          class="connection-list"
          role="tabpanel"
          :aria-label="`${activeCategoryLabel} 연결 목록`"
          tabindex="0"
        >
        <article
          v-for="group in visibleConnections"
          :key="group.groupKey"
          class="connection-institution"
        >
          <div class="connection-institution-header d-flex align-items-center gap-3">
            <div class="asset-logo" aria-hidden="true">
              <img
                v-if="getConnectionLogoUrl(group)"
                :src="getConnectionLogoUrl(group)"
                :alt="`${group.institutionName} 로고`"
                :data-fallback-src="getConnectionFallbackLogoUrl(group)"
                class="asset-logo-image"
                @error="handleLogoError"
              />
              <span :class="getLogoFallbackClass(getConnectionLogoUrl(group))">
                {{ getLogoText(group) }}
              </span>
            </div>

            <div class="connection-information flex-grow-1 min-width-0">
              <strong class="d-block text-truncate">{{ group.institutionName }}</strong>
              <small class="d-block text-secondary">
                연결된 {{ group.assets.length }}개 자산
              </small>
              <small class="d-block text-secondary">
                {{ formatLastSync(group.lastSyncAt) }}
              </small>
            </div>

            <AppButton
              type="button"
              class="connection-disconnect text-nowrap"
              variant="ghost"
              size="sm"
              :disabled="disconnectingId !== null"
              @click="openDisconnectModal(group)"
            >
              <span
                v-if="disconnectingId === group.connectionId"
                class="spinner-border spinner-border-sm me-1"
                aria-hidden="true"
              ></span>
              <template v-if="disconnectingId !== group.connectionId">연결 해제</template>
            </AppButton>
          </div>

          <ul class="connection-asset-list mb-0">
            <li
              v-for="asset in group.visibleAssets"
              :key="`${asset.connectionId}-${asset.assetKind}-${asset.assetId}`"
              class="connection-asset d-flex align-items-center gap-3"
            >
              <div class="connection-information flex-grow-1 min-width-0">
                <strong class="d-block text-truncate">{{ getAssetTypeLabel(asset) }}</strong>
                <small
                  class="d-block text-secondary text-truncate"
                  :title="asset.assetName || asset.displayNumber"
                >
                  {{ asset.assetName || "자산" }} · {{ asset.displayNumber || "번호 정보 없음" }}
                </small>
              </div>

              <strong class="connection-amount text-nowrap">
                <span v-if="asset.assetKind === 'CARD'" class="connection-amount-label">
                  이번 달
                </span>
                {{ formatAmount(asset.amount, asset.currency) }}
              </strong>
            </li>
          </ul>
        </article>
        </div>

        <div
          v-else
          :id="`connection-category-panel-${activeCategory.toLowerCase()}`"
          class="connection-empty-state text-center"
          role="tabpanel"
          :aria-label="`${activeCategoryLabel} 연결 목록`"
          tabindex="0"
        >
          <AppState
            class="connection-empty-state-ui"
            type="empty"
            :title="activeCategoryEmptyMessage"
            :message="`금융기관을 연동하면 ${activeCategoryLabel} 정보를 이곳에서 관리할 수 있습니다.`"
            compact
          />
        </div>

        <RouterLink to="/connections/mydata" class="connection-add-button pressable">
          <i class="bi bi-plus-lg me-1" aria-hidden="true"></i>
          자산 연동 추가
        </RouterLink>
      </div>
    </AppCard>

    <Teleport to="body">
      <div
        v-if="pendingDisconnectConnection"
        class="connection-modal-backdrop"
        role="presentation"
        @click.self="closeDisconnectModal"
      >
      <section
        ref="disconnectModalRef"
        class="connection-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="disconnect-modal-title"
        aria-describedby="disconnect-modal-description"
        tabindex="-1"
        @keydown="handleModalKeydown"
      >
        <div class="connection-modal-header">
          <h3 id="disconnect-modal-title" class="h6 fw-bold mb-0">연결 해제</h3>
          <button
            type="button"
          class="btn-close pressable"
            aria-label="모달 닫기"
            :disabled="disconnectingId !== null"
            @click="closeDisconnectModal"
          ></button>
        </div>

        <div class="connection-modal-body">
          <p class="fw-semibold mb-2">
            {{ pendingDisconnectConnection.institutionName }} 연결을 해제하시겠습니까?
          </p>
          <p id="disconnect-modal-description" class="small text-secondary mb-2">
            연결을 해제하면 아래 계좌·카드·증권이 현재 자산에서 제외됩니다.
          </p>

          <ul class="connection-modal-assets mb-0">
            <li
              v-for="asset in pendingDisconnectConnection.assets"
              :key="`${asset.connectionId}-${asset.assetKind}-${asset.assetId}`"
              class="connection-modal-asset d-flex align-items-center justify-content-between gap-3"
            >
              <div class="min-width-0">
                <small class="d-block text-body text-truncate">
                  {{ asset.assetName || "연결 자산" }} ·
                  {{ asset.displayNumber || "번호 정보 없음" }}
                </small>
              </div>
            </li>
          </ul>

          <p class="small text-secondary mt-3 mb-0">
            거래 내역과 과거 자산 기록은 유지됩니다. 현재 자산에서는 제외되며, 이번 달 자산 총액과
            스냅샷은 해제 후 금액으로 갱신됩니다.
          </p>

          <div v-if="disconnectModalError" class="alert alert-danger py-2 mt-3 mb-0" role="alert">
            {{ disconnectModalError }}
          </div>
        </div>

        <div class="connection-modal-footer d-flex gap-2">
          <AppButton
            type="button"
            class="btn-light flex-fill"
            variant="secondary"
            :disabled="disconnectingId !== null"
            @click="closeDisconnectModal"
          >
            취소
          </AppButton>
          <AppButton
            type="button"
            class="btn-danger flex-fill"
            variant="danger"
            :disabled="disconnectingId !== null"
            :loading="disconnectingId !== null"
            @click="handleDisconnect"
          >
            연결 해제
          </AppButton>
        </div>
      </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.settings-panel {
  min-height: 420px;
  border-radius: var(--wallo-radius-xl);
}

.connection-state {
  min-height: 420px;
}

.connection-card-body {
  padding: var(--wallo-space-6);
}

.connection-alert {
  margin-bottom: var(--wallo-space-4);
}

.connection-refresh-status {
  margin: var(--wallo-space-3) var(--wallo-space-3) 0;
  padding: var(--wallo-space-2) var(--wallo-space-3);
  color: var(--wallo-color-text-muted);
  border-radius: var(--wallo-radius-md);
  background: var(--wallo-color-info-bg);
  font-size: 0.85rem;
}

.connection-section-heading {
  padding: 0 0 var(--wallo-space-3);
  border-bottom: 1px solid var(--wallo-color-border-soft);
}

.connection-category-tabs {
  margin-bottom: var(--wallo-space-4);
}

.connection-category-count {
  margin-left: var(--wallo-space-1);
  color: var(--wallo-color-text-muted);
  font-size: 0.75rem;
  font-weight: 700;
}

.connection-list {
  display: flex;
  flex-direction: column;
  gap: var(--wallo-space-3);
  padding: var(--wallo-space-5) 0 var(--wallo-space-4);
}

.connection-institution {
  min-width: 0;
  padding: var(--wallo-space-4);
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: var(--wallo-radius-md);
}

.connection-institution-header {
  min-width: 0;
  padding-bottom: var(--wallo-space-3);
  border-bottom: 1px solid var(--wallo-color-border-soft);
}

.connection-information {
  min-width: 0;
}

.connection-asset-list,
.connection-modal-assets {
  padding-left: 0;
  list-style: none;
}

.connection-asset-list {
  display: flex;
  flex-direction: column;
  gap: var(--wallo-space-1);
  padding-top: var(--wallo-space-2);
}

.connection-asset {
  min-width: 0;
  padding: var(--wallo-space-3) 0 var(--wallo-space-2) 62px;
}

.connection-asset + .connection-asset {
  border-top: 1px solid var(--wallo-color-border-soft);
}

.connection-amount {
  color: var(--wallo-color-text);
  font-size: 14px;
}

.connection-amount-label {
  display: block;
  color: var(--wallo-color-text-muted);
  font-size: 11px;
  font-weight: 400;
  text-align: right;
}

.connection-disconnect {
  color: var(--wallo-color-text-muted);
  font-size: 0.8rem;
  text-decoration: none;
}

.connection-disconnect:hover {
  color: var(--wallo-color-primary);
}

.connection-modal-assets {
  max-height: 220px;
  margin-right: -4px;
  overflow-y: auto;
  padding-right: 4px;
}

.connection-modal-asset {
  padding: var(--wallo-space-3) 0;
  border-bottom: 1px solid var(--wallo-color-border-soft);
}

.connection-modal-asset:last-child {
  border-bottom: 0;
}

.connection-empty-state {
  padding: 0;
}

.connection-empty-state :deep(.connection-empty-state-ui) {
  min-height: 220px;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.connection-add-button {
  display: block;
  padding: 12px;
  border: 1px dashed #cbd7e8;
  border-radius: 12px;
  color: #6f7f96;
  text-align: center;
  text-decoration: none;
}

.connection-add-button:hover,
.connection-add-button:focus-visible {
  border-color: #5c94df;
  color: #3e7bd1;
  background: #f5faff;
}

.connection-add-button:focus-visible {
  outline: 3px solid rgb(79 143 232 / 22%);
  outline-offset: 2px;
}

.connection-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1050;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgb(19 23 43 / 58%);
}

.connection-modal {
  width: min(100%, 420px);
  overflow: hidden;
  border-radius: var(--wallo-radius-lg);
  background: var(--wallo-color-surface);
  box-shadow: var(--wallo-shadow-modal);
}

.connection-modal-header,
.connection-modal-footer {
  padding: 18px 20px;
}

.connection-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--wallo-color-border-soft);
}

.connection-modal-body {
  padding: 20px;
}

.connection-modal-footer {
  border-top: 1px solid var(--wallo-color-border-soft);
}

@media (max-width: 576px) {
  .connection-card-body {
    padding: var(--wallo-space-5);
  }

  .connection-institution-header {
    flex-wrap: wrap;
  }

  .connection-institution-header .connection-disconnect {
    margin-left: auto;
  }

  .connection-asset {
    flex-wrap: wrap;
    padding-left: 0;
  }

  .connection-asset .connection-amount {
    margin-left: auto;
  }

  .connection-disconnect {
    margin-left: auto;
  }
}
</style>
