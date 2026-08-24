<script setup>
import { getExpenseCategoryMeta } from "@/features/financial/financialCategories";
import { formatNumber } from "@/utils/formatters";

const props = defineProps({
  transactions: {
    type: Array,
    default: () => [],
  },
  hasNext: {
    type: Boolean,
    default: false,
  },
  isLoadingMore: {
    type: Boolean,
    default: false,
  },
  loadMoreError: {
    type: String,
    default: "",
  },
  emptyMessage: {
    type: String,
    default: "이 달의 거래 내역이 없습니다.",
  },
  editable: {
    type: Boolean,
    default: true,
  },
});

const emit = defineEmits(["load-more", "edit-category"]);

const formatDate = (date) => {
  const [, month, day] = String(date || "").split("-");
  return month && day ? `${Number(month)}월 ${Number(day)}일` : date;
};

const transactionInstitutionPrefixes = [
  "KB국민카드",
  "국민카드",
  "신한카드",
  "하나카드",
  "우리카드",
  "삼성카드",
  "현대카드",
  "롯데카드",
  "NH농협카드",
  "농협카드",
  "KB국민은행",
  "국민은행",
  "신한은행",
  "하나은행",
  "우리은행",
  "농협은행",
  "기업은행",
  "국민",
  "신한",
  "하나",
  "우리",
  "농협",
  "기업",
];

const displayMerchantName = (merchantName) => {
  const normalizedName = String(merchantName || "").trim();
  const prefix = transactionInstitutionPrefixes.find(
    (candidate) => normalizedName.startsWith(`${candidate} `),
  );

  return prefix ? normalizedName.slice(prefix.length).trim() : normalizedName;
};

const formatAmount = (transaction) => {
  const amount = formatNumber(transaction.amount);
  const isIncomingTransfer = transaction.type === "TRANSFER" && transaction.category === "RECEIVE";
  const isOutgoingTransfer = transaction.type === "TRANSFER" && transaction.category === "SEND";

  if (transaction.type === "INCOME" || isIncomingTransfer) {
    return `+${amount}원`;
  }
  if (transaction.type === "EXPENSE" || isOutgoingTransfer) {
    return `-${amount}원`;
  }
  return `${amount}원`;
};

const typeLabel = (transaction) => {
  if (transaction.type === "TRANSFER") {
    return transaction.category === "RECEIVE" ? "받은 돈" : "보낸 돈";
  }

  return {
    EXPENSE: "지출",
    INCOME: "입금",
  }[transaction.type] ?? transaction.type;
};
</script>

<template>
  <div>
    <ul v-if="transactions.length" class="transaction-list list-unstyled mb-0">
      <li v-for="(transaction, index) in transactions" :key="transaction.transactionId || `${transaction.date}-${transaction.merchantName}-${transaction.amount}-${index}`">
        <button
          v-if="props.editable && transaction.transactionId"
          type="button"
          class="transaction-icon transaction-icon-button pressable"
          :class="getExpenseCategoryMeta(transaction.category).colorClass"
          data-testid="transaction-category-button"
          aria-label="카테고리 수정"
          title="카테고리 수정"
          @click="emit('edit-category', transaction)"
        >
          <i :class="['bi', getExpenseCategoryMeta(transaction.category).icon]" aria-hidden="true"></i>
        </button>
        <span
          v-else
          class="transaction-icon"
          :class="getExpenseCategoryMeta(transaction.category).colorClass"
        >
          <i :class="['bi', getExpenseCategoryMeta(transaction.category).icon]" aria-hidden="true"></i>
        </span>
        <span class="transaction-info">
          <strong>{{ displayMerchantName(transaction.merchantName) }}</strong>
          <small>
            {{ formatDate(transaction.date) }} · {{ getExpenseCategoryMeta(transaction.category).label }} ·
            {{ typeLabel(transaction) }}
          </small>
        </span>
        <div class="transaction-actions">
          <strong class="transaction-amount" :class="String(transaction.type || '').toLowerCase()">
            {{ formatAmount(transaction) }}
          </strong>
        </div>
      </li>
    </ul>

    <div v-else class="transaction-empty text-center">
      <i class="bi bi-receipt text-secondary fs-2" aria-hidden="true"></i>
      <p class="text-secondary mb-0 mt-2">{{ emptyMessage }}</p>
    </div>

    <p v-if="loadMoreError" class="small text-danger text-center mb-2 mt-3" role="alert">
      {{ loadMoreError }}
    </p>

    <div v-if="hasNext" class="text-center mt-4">
      <button
        type="button"
        class="btn load-more-button pressable"
        :disabled="isLoadingMore"
        @click="$emit('load-more')"
      >
        <span v-if="isLoadingMore" class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>
        {{ isLoadingMore ? "불러오는 중" : "더보기" }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.transaction-list {
  display: grid;
  gap: 12px;
}

.transaction-list li {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 15px 16px;
  border: 1px solid #edf0f5;
  border-radius: 16px;
  background: #ffffff;
}

.transaction-icon {
  display: inline-flex;
  width: 42px;
  height: 42px;
  align-items: center;
  justify-content: center;
  border-radius: 13px;
  font-size: 1.05rem;
}

.transaction-icon-button {
  padding: 0;
  border: 0;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.transaction-icon-button:hover,
.transaction-icon-button:focus-visible {
  transform: translateY(-1px);
  box-shadow: 0 0 0 3px rgba(129, 112, 255, 0.16);
}

.transaction-icon-button:active:not(:disabled) {
  transform: translateY(-1px) scale(0.98);
}

.transaction-icon-button:focus-visible {
  outline: 2px solid #4d82d6;
  outline-offset: 2px;
}

.transaction-icon.coral { color: #ff796f; background: #fff0ed; }
.transaction-icon.green { color: #28b98a; background: #eafaf4; }
.transaction-icon.blue { color: #4f73e8; background: #edf2ff; }
.transaction-icon.purple { color: #6b9be3; background: #edf6ff; }
.transaction-icon.gray { color: #7c8294; background: #f1f3f6; }

.transaction-info {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.transaction-info strong {
  overflow: hidden;
  color: #343044;
  font-size: 0.95rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transaction-info small {
  color: #8a90a2;
}

.transaction-amount {
  color: #656b7c;
  font-size: 0.92rem;
  white-space: nowrap;
}

.transaction-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.transaction-amount.income {
  color: #4f73e8;
}

.transaction-empty {
  display: flex;
  min-height: 230px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.load-more-button {
  min-width: 150px;
  border: 1px solid #6b9be3;
  border-radius: 12px;
  color: #4d82d6;
  background: #ffffff;
  font-weight: 700;
}

.load-more-button:hover:not(:disabled),
.load-more-button:focus:not(:disabled) {
  color: #ffffff;
  background: #6b9be3;
}

@media (max-width: 575.98px) {
  .transaction-list li {
    grid-template-columns: 40px minmax(0, 1fr);
  }

  .transaction-actions {
    grid-column: 2;
    justify-content: space-between;
  }
}
</style>
