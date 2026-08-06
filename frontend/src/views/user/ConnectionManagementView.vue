<script setup>
import { computed, onMounted, ref } from "vue"
import { RouterLink } from "vue-router"
import { getApiErrorMessage } from "@/commonUtils/apiError"
import { disconnectConnection, getConnections } from "@/api/connectionApi"
import { getLocalInstitutionLogo } from "@/features/asset/institutionLogos"
import { useAssetStore } from "@/stores/assetStore"

const assetStore = useAssetStore()
const connections = ref([])
const isLoading = ref(false)
const errorMessage = ref("")
const successMessage = ref("")
const disconnectingId = ref(null)
const pendingDisconnectConnection = ref(null)
const disconnectModalError = ref("")
const connectionCategories = Object.freeze([
  { key: "ACCOUNT", label: "계좌", emptyMessage: "연결된 계좌가 없습니다." },
  { key: "CARD", label: "카드", emptyMessage: "연결된 카드가 없습니다." },
  { key: "STOCK", label: "증권", emptyMessage: "연결된 증권이 없습니다." },
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
    const groupKey = asset.connectionId
      ?? `institution-${asset.institutionId ?? "unknown"}-${asset.institutionName ?? "unknown"}`
    const existingGroup = groupedConnections.get(groupKey)

    if (existingGroup) {
      existingGroup.assets.push(asset)
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

const visibleConnections = computed(() => (
  categorizedConnections.value[activeCategory.value] || []
))

const activeCategoryLabel = computed(() => (
  connectionCategories.find(({ key }) => key === activeCategory.value)?.label || "계좌"
))

const activeCategoryEmptyMessage = computed(() => (
  connectionCategories.find(({ key }) => key === activeCategory.value)?.emptyMessage
    || "연결된 계좌가 없습니다."
))

const getCategoryCount = (categoryKey) => (
  categorizedConnections.value[categoryKey]?.length || 0
)

const activeCategoryAssetCount = computed(() => (
  visibleConnections.value.reduce(
    (total, group) => total + group.visibleAssets.length,
    0,
  )
))

const formatAmount = (amount) =>
  `${new Intl.NumberFormat("ko-KR").format(Number(amount) || 0)}원`

const getAssetTypeLabel = (connection) => {
  if (connection.assetKind === "CARD") {
    return connection.assetType === "CHECK" || connection.assetType === "DEBIT"
      ? "체크카드"
      : "신용카드"
  }

  return {
    BANK: "입출금",
    STOCK: "투자계좌",
    LOAN: "대출",
  }[connection.assetType] || "계좌"
}

const getLogoText = (connection) => {
  const name = connection.institutionName || "금융"
  return name.replace(/\s/g, "").slice(0, 2)
}

const getConnectionLogoUrl = (connection) => (
  connection.logoUrl
  || getLocalInstitutionLogo(
    connection.financialGroupCode,
    connection.financialGroupName || connection.institutionName,
  )
)

const getConnectionFallbackLogoUrl = (connection) => (
  connection.logoUrl
    ? getLocalInstitutionLogo(
      connection.financialGroupCode,
      connection.financialGroupName || connection.institutionName,
    )
    : ""
)

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

const loadConnections = async () => {
  isLoading.value = true
  errorMessage.value = ""

  try {
    const response = await getConnections()
    connections.value = response?.connections || []
  } catch (error) {
    connections.value = []
    errorMessage.value = getApiErrorMessage(
      error,
      "연결된 자산 정보를 불러오지 못했습니다.",
    )
  } finally {
    isLoading.value = false
  }
}

const openDisconnectModal = (connection) => {
  pendingDisconnectConnection.value = connection
  disconnectModalError.value = ""
}

const closeDisconnectModal = () => {
  if (disconnectingId.value !== null) {
    return
  }

  pendingDisconnectConnection.value = null
  disconnectModalError.value = ""
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
    connections.value = connections.value.filter(
      (item) => item.connectionId !== connection.connectionId,
    )
    pendingDisconnectConnection.value = null

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
    disconnectModalError.value = getApiErrorMessage(
      error,
      "자산 연결을 해제하지 못했습니다.",
    )
  } finally {
    disconnectingId.value = null
  }
}

onMounted(loadConnections)
</script>

<template>
  <section class="settings-panel card border-0 shadow-sm" aria-labelledby="connection-settings-title">
    <div v-if="isLoading" class="connection-state text-center" aria-live="polite">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">연결 정보를 불러오는 중</span>
      </div>
      <p class="text-secondary mb-0 mt-3">연결된 자산 정보를 불러오고 있습니다.</p>
    </div>

    <div v-else class="card-body p-4 p-md-5">
      <h2 id="connection-settings-title" class="h5 fw-bold mb-4">연결된 자산</h2>

      <div v-if="errorMessage" class="alert alert-danger py-2" role="alert">
        <div class="d-flex align-items-center justify-content-between gap-3">
          <span>{{ errorMessage }}</span>
          <button type="button" class="btn btn-sm btn-outline-danger" @click="loadConnections">
            다시 시도
          </button>
        </div>
      </div>

      <div v-if="successMessage" class="alert alert-success py-2" role="status">
        {{ successMessage }}
      </div>

      <div class="connection-category-tabs" role="tablist" aria-label="연결된 자산 유형">
        <button
          v-for="category in connectionCategories"
          :key="category.key"
          type="button"
          class="connection-category-tab"
          :class="{ 'connection-category-tab-active': activeCategory === category.key }"
          role="tab"
          :aria-selected="activeCategory === category.key"
          aria-controls="connection-category-panel"
          @click="activeCategory = category.key"
        >
          {{ category.label }}
          <span class="connection-category-count">{{ getCategoryCount(category.key) }}</span>
        </button>
      </div>

      <div class="connection-section-heading d-flex align-items-center justify-content-between gap-3">
        <span class="small text-secondary">
          {{ activeCategoryLabel }} 연결 기관 {{ visibleConnections.length }}곳 · 자산 {{ activeCategoryAssetCount }}개
        </span>
      </div>

      <div
        v-if="visibleConnections.length > 0"
        id="connection-category-panel"
        class="connection-list"
        role="tabpanel"
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
            </div>

            <button
              type="button"
              class="btn btn-link connection-disconnect text-nowrap"
              :disabled="disconnectingId !== null"
              @click="openDisconnectModal(group)"
            >
              <span
                v-if="disconnectingId === group.connectionId"
                class="spinner-border spinner-border-sm me-1"
                aria-hidden="true"
              ></span>
              연결 해제
            </button>
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
                {{ formatAmount(asset.amount) }}
              </strong>
            </li>
          </ul>
        </article>
      </div>

      <div
        v-else
        id="connection-category-panel"
        class="connection-empty-state text-center"
        role="tabpanel"
        tabindex="0"
      >
        <i class="bi bi-wallet2 fs-2 text-secondary" aria-hidden="true"></i>
        <p class="fw-semibold mb-1 mt-3">{{ activeCategoryEmptyMessage }}</p>
        <p class="small text-secondary mb-4">
          금융기관을 연동하면 {{ activeCategoryLabel }} 정보를 이곳에서 관리할 수 있습니다.
        </p>
      </div>

      <RouterLink to="/connections/mydata" class="connection-add-button">
        <i class="bi bi-plus-lg me-1" aria-hidden="true"></i>
        자산 연동 추가
      </RouterLink>
    </div>
  </section>

  <Teleport to="body">
    <div
      v-if="pendingDisconnectConnection"
      class="connection-modal-backdrop"
      role="presentation"
      @click.self="closeDisconnectModal"
    >
      <section
        class="connection-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="disconnect-modal-title"
      >
        <div class="connection-modal-header">
          <h3 id="disconnect-modal-title" class="h6 fw-bold mb-0">연결 해제</h3>
          <button
            type="button"
            class="btn-close"
            aria-label="모달 닫기"
            :disabled="disconnectingId !== null"
            @click="closeDisconnectModal"
          ></button>
        </div>

        <div class="connection-modal-body">
          <p class="fw-semibold mb-2">
            {{ pendingDisconnectConnection.institutionName }} 연결을 해제하시겠습니까?
          </p>
          <p class="small text-secondary mb-2">
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
                  {{ asset.assetName || "연결 자산" }} · {{ asset.displayNumber || "번호 정보 없음" }}
                </small>
              </div>
            </li>
          </ul>

          <p class="small text-secondary mt-3 mb-0">
            거래 내역과 과거 자산 기록은 유지됩니다. 현재 자산에서는 제외되며, 이번 달 자산 총액과 스냅샷은 해제 후 금액으로 갱신됩니다.
          </p>

          <div v-if="disconnectModalError" class="alert alert-danger py-2 mt-3 mb-0" role="alert">
            {{ disconnectModalError }}
          </div>
        </div>

        <div class="connection-modal-footer d-flex gap-2">
          <button
            type="button"
            class="btn btn-light flex-fill"
            :disabled="disconnectingId !== null"
            @click="closeDisconnectModal"
          >
            취소
          </button>
          <button
            type="button"
            class="btn btn-danger flex-fill"
            :disabled="disconnectingId !== null"
            @click="handleDisconnect"
          >
            <span
              v-if="disconnectingId !== null"
              class="spinner-border spinner-border-sm me-1"
              aria-hidden="true"
            ></span>
            연결 해제
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.settings-panel {
  min-height: 420px;
  border-radius: 20px;
  background: #ffffff;
}

.connection-state {
  display: flex;
  min-height: 420px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.connection-section-heading {
  padding: 0 0 12px;
  border-bottom: 1px solid #eef0f5;
}

.connection-category-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding: 4px;
  border-radius: 12px;
  background: #f7f8fc;
}

.connection-category-tab {
  flex: 1;
  padding: 10px 12px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: #7f8ba0;
  font-size: 14px;
  font-weight: 600;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.connection-category-tab:hover,
.connection-category-tab:focus-visible,
.connection-category-tab-active {
  background: #ffffff;
  color: #4f46c7;
}

.connection-category-tab-active {
  box-shadow: 0 2px 8px rgb(28 35 52 / 8%);
}

.connection-category-count {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 500;
}

.connection-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px 0 20px;
}

.connection-institution {
  min-width: 0;
  padding: 14px;
  border: 1px solid #e8edf4;
  border-radius: 14px;
}

.connection-institution-header {
  min-width: 0;
  padding-bottom: 14px;
  border-bottom: 1px solid #eef0f5;
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
  gap: 4px;
  padding-top: 8px;
}

.connection-asset {
  min-width: 0;
  padding: 10px 0 6px 62px;
}

.connection-asset + .connection-asset {
  border-top: 1px solid #f0f2f6;
}

.connection-amount {
  color: #27324a;
  font-size: 14px;
}

.connection-amount-label {
  display: block;
  color: #8b96a8;
  font-size: 11px;
  font-weight: 400;
  text-align: right;
}

.connection-disconnect {
  padding: 0;
  color: #7f8ba0;
  font-size: 13px;
  text-decoration: none;
}

.connection-disconnect:hover {
  color: #4f46c7;
}

.connection-modal-assets {
  max-height: 220px;
  margin-right: -4px;
  overflow-y: auto;
  padding-right: 4px;
}

.connection-modal-asset {
  padding: 10px 0;
  border-bottom: 1px solid #eef0f5;
}

.connection-modal-asset:last-child {
  border-bottom: 0;
}

.connection-empty-state {
  padding: 72px 16px 56px;
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

.connection-add-button:hover {
  border-color: #6366f1;
  color: #4f46c7;
}

.connection-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1050;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgb(28 35 52 / 45%);
}

.connection-modal {
  width: min(100%, 420px);
  overflow: hidden;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 18px 50px rgb(28 35 52 / 22%);
}

.connection-modal-header,
.connection-modal-footer {
  padding: 18px 20px;
}

.connection-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eef0f5;
}

.connection-modal-body {
  padding: 20px;
}

.connection-modal-footer {
  border-top: 1px solid #eef0f5;
}

@media (max-width: 576px) {
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
