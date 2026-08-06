<script setup>
import { onMounted, ref } from "vue"
import { RouterLink } from "vue-router"
import { getApiErrorMessage } from "@/commonUtils/apiError"
import { disconnectConnection, getConnections } from "@/api/connectionApi"

const connections = ref([])
const isLoading = ref(false)
const errorMessage = ref("")
const successMessage = ref("")
const disconnectingId = ref(null)
const pendingDisconnectConnection = ref(null)
const disconnectModalError = ref("")

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
  return name.slice(0, 2)
}

const handleLogoError = (event) => {
  event.target.style.display = "none"
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
      "연결된 계좌·카드 정보를 불러오지 못했습니다.",
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
    successMessage.value = `${institutionName} 연결이 해제되었습니다.`
  } catch (error) {
    disconnectModalError.value = getApiErrorMessage(
      error,
      "계좌·카드 연결을 해제하지 못했습니다.",
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
      <p class="text-secondary mb-0 mt-3">연결된 계좌·카드 정보를 불러오고 있습니다.</p>
    </div>

    <div v-else class="card-body p-4 p-md-5">
      <h2 id="connection-settings-title" class="h5 fw-bold mb-4">연결된 계좌·카드</h2>

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

      <div class="connection-section-heading d-flex align-items-center justify-content-between gap-3">
        <span class="small fw-semibold text-secondary">연결된 계좌·카드</span>
        <span class="small text-secondary">{{ connections.length }}개</span>
      </div>

      <div v-if="connections.length > 0" class="connection-list">
        <article
          v-for="connection in connections"
          :key="`${connection.connectionId}-${connection.assetKind}-${connection.assetId}`"
          class="connection-item d-flex align-items-center gap-3"
        >
          <div class="connection-logo" aria-hidden="true">
            <span class="connection-logo-fallback">{{ getLogoText(connection) }}</span>
            <img
              v-if="connection.logoUrl"
              :src="connection.logoUrl"
              :alt="`${connection.institutionName} 로고`"
              @error="handleLogoError"
            />
          </div>

          <div class="connection-information flex-grow-1 min-width-0">
            <strong class="d-block text-truncate">{{ connection.institutionName }}</strong>
            <small
              class="d-block text-secondary text-truncate"
              :title="connection.assetName || connection.displayNumber"
            >
              {{ getAssetTypeLabel(connection) }} · {{ connection.displayNumber || "번호 정보 없음" }}
            </small>
          </div>

          <strong class="connection-amount text-nowrap">
            <span v-if="connection.assetKind === 'CARD'" class="connection-amount-label">
              이번 달
            </span>
            {{ formatAmount(connection.amount) }}
          </strong>

          <button
            type="button"
            class="btn btn-link connection-disconnect text-nowrap"
            :disabled="disconnectingId !== null"
            @click="openDisconnectModal(connection)"
          >
            <span
              v-if="disconnectingId === connection.connectionId"
              class="spinner-border spinner-border-sm me-1"
              aria-hidden="true"
            ></span>
            연결 해제
          </button>
        </article>
      </div>

      <div v-else class="connection-empty-state text-center">
        <i class="bi bi-wallet2 fs-2 text-secondary" aria-hidden="true"></i>
        <p class="fw-semibold mb-1 mt-3">연결된 계좌·카드가 없습니다.</p>
        <p class="small text-secondary mb-4">
          금융기관을 연동하면 계좌와 카드 정보를 이곳에서 관리할 수 있습니다.
        </p>
      </div>

      <RouterLink to="/connections/mydata" class="connection-add-button">
        <i class="bi bi-plus-lg me-1" aria-hidden="true"></i>
        계좌·카드 연동 추가
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
          <p class="small text-secondary mb-0">
            {{ getAssetTypeLabel(pendingDisconnectConnection) }} 정보와 연결된 자산 조회가 중단됩니다.
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

.connection-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px 0 20px;
}

.connection-item {
  min-width: 0;
  padding: 14px;
  border: 1px solid #e8edf4;
  border-radius: 14px;
}

.connection-logo {
  position: relative;
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: #eef0ff;
  color: #4f46c7;
  font-size: 12px;
  font-weight: 700;
}

.connection-logo img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: #ffffff;
}

.connection-information {
  min-width: 0;
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
  .connection-item {
    flex-wrap: wrap;
  }

  .connection-amount {
    margin-left: 54px;
  }

  .connection-disconnect {
    margin-left: auto;
  }
}
</style>
