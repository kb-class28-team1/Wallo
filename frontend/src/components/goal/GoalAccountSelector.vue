<script setup>
import { computed, ref, watch } from "vue";
import { formatWon } from "@/commonUtils/formatters";

const props = defineProps({
  accounts: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  saving: {
    type: Boolean,
    default: false,
  },
  error: {
    type: String,
    default: null,
  },
});

const emit = defineEmits(["retry", "select-account"]);
const selectedAccountId = ref(null);

const savedAccountId = computed(() => {
  const selectedAccount = props.accounts.find((account) => account.selected);
  return selectedAccount?.accountId ?? null;
});

const accountSelectionChanged = computed(() => (
  selectedAccountId.value !== null
  && Number(selectedAccountId.value) !== Number(savedAccountId.value)
));

watch(
  () => props.accounts,
  (accounts) => {
    const selectedAccount = accounts.find((account) => account.selected);
    selectedAccountId.value = selectedAccount?.accountId ?? null;
  },
  { deep: true, immediate: true },
);

const formatAccountBalance = (account) => {
  if (!account?.currency || account.currency === "KRW") {
    return formatWon(account?.balance);
  }

  return `${new Intl.NumberFormat("ko-KR").format(Number(account.balance) || 0)} ${account.currency}`;
};

const submitAccountSelection = () => {
  if (!accountSelectionChanged.value) {
    return;
  }

  emit("select-account", selectedAccountId.value);
};
</script>

<template>
  <section class="goal-account-selection" aria-labelledby="goal-account-selection-title">
    <div class="goal-account-heading d-flex align-items-start justify-content-between gap-3">
      <div>
        <h3 id="goal-account-selection-title" class="h6 fw-bold mb-1">
          목표에 사용할 계좌
        </h3>
        <p class="small text-secondary mb-0">
          연결된 계좌 중 하나를 선택해 주세요.
        </p>
      </div>
      <span v-if="accounts.length > 0" class="small text-secondary text-nowrap">
        {{ accounts.length }}개 사용 가능
      </span>
    </div>

    <div v-if="loading" class="goal-account-state text-secondary" role="status">
      <span class="spinner-border spinner-border-sm text-primary me-2" aria-hidden="true"></span>
      사용 가능한 계좌를 불러오는 중입니다.
    </div>

    <div v-else class="mt-3">
      <div v-if="error" class="alert alert-danger py-2" role="alert">
        <div class="d-flex align-items-center justify-content-between gap-3">
          <span>{{ error }}</span>
          <button
            type="button"
            class="btn btn-sm btn-outline-danger text-nowrap"
            @click="emit('retry')"
          >
            다시 시도
          </button>
        </div>
      </div>

      <div v-if="accounts.length === 0" class="goal-account-empty text-secondary">
        <p class="mb-1 fw-semibold text-dark">사용 가능한 계좌가 없습니다.</p>
        <p class="small mb-0">입출금·예금·적금·CMA 계좌를 연결해 주세요.</p>
      </div>

      <div v-else>
        <div class="goal-account-list" role="radiogroup" aria-label="목표에 사용할 계좌 선택">
          <label
            v-for="account in accounts"
            :key="account.accountId"
            class="goal-account-option"
            :class="{ 'goal-account-option-selected': Number(selectedAccountId) === Number(account.accountId) }"
          >
            <input
              v-model="selectedAccountId"
              class="form-check-input mt-1"
              type="radio"
              name="goal-account"
              :value="account.accountId"
            />
            <span class="goal-account-content">
              <span class="d-flex align-items-center justify-content-between gap-3">
                <span class="min-w-0">
                  <strong class="d-block text-truncate">{{ account.bankName }}</strong>
                  <span class="small text-secondary d-block text-truncate">
                    {{ account.accountName }} · {{ account.displayNumber }}
                  </span>
                </span>
                <span class="text-end text-nowrap">
                  <span class="goal-account-type d-block">{{ account.accountType }}</span>
                  <strong class="d-block">{{ formatAccountBalance(account) }}</strong>
                </span>
              </span>
            </span>
          </label>
        </div>

        <button
          type="button"
          class="btn btn-primary w-100 mt-3"
          :disabled="!accountSelectionChanged || saving"
          @click="submitAccountSelection"
        >
          <span
            v-if="saving"
            class="spinner-border spinner-border-sm me-2"
            aria-hidden="true"
          ></span>
          {{ saving ? "계좌 저장 중..." : "이 계좌를 목표 계좌로 선택" }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.goal-account-selection {
  margin-top: 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid #eef0f5;
}

.goal-account-heading {
  min-width: 0;
}

.goal-account-state,
.goal-account-empty {
  padding: 1rem;
  border-radius: 14px;
  background: #f8f9fc;
  text-align: center;
}

.goal-account-list {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.goal-account-option {
  display: flex;
  gap: 0.75rem;
  margin: 0;
  padding: 0.85rem 1rem;
  border: 1px solid #e4e8f0;
  border-radius: 14px;
  background: #ffffff;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.goal-account-option:hover {
  border-color: #aaa4ee;
  background: #fafaff;
}

.goal-account-option-selected {
  border-color: #6559e8;
  background: #f7f6ff;
  box-shadow: 0 0 0 2px rgb(101 89 232 / 10%);
}

.goal-account-content {
  min-width: 0;
  flex: 1;
}

.goal-account-type {
  color: #6559e8;
  font-size: 0.75rem;
  font-weight: 700;
}
</style>
