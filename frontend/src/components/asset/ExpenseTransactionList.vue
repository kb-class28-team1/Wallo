<script setup>
import { getExpenseCategoryMeta } from "@/features/financial/financialCategories";
import { formatNumber } from "@/utils/formatters";

defineProps({
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
});

defineEmits(["load-more"]);

const formatDate = (date) => {
  const [, month, day] = String(date || "").split("-");
  return month && day ? `${Number(month)}월 ${Number(day)}일` : date;
};

const formatAmount = (transaction) => {
  const amount = formatNumber(transaction.amount);
  if (transaction.type === "INCOME") return `+${amount}원`;
  if (transaction.type === "EXPENSE" || transaction.type === "TRANSFER") return `-${amount}원`;
  return `${amount}원`;
};

const typeLabel = (type) => ({
  EXPENSE: "지출",
  INCOME: "입금",
  TRANSFER: "출금 이체",
}[type] ?? type);
</script>

<template>
  <div>
    <ul v-if="transactions.length" class="transaction-list list-unstyled mb-0">
      <li v-for="(transaction, index) in transactions" :key="`${transaction.date}-${transaction.merchantName}-${transaction.amount}-${index}`">
        <span class="transaction-icon" :class="getExpenseCategoryMeta(transaction.category).colorClass">
          <i :class="['bi', getExpenseCategoryMeta(transaction.category).icon]" aria-hidden="true"></i>
        </span>
        <span class="transaction-info">
          <strong>{{ transaction.merchantName }}</strong>
          <small>
            {{ formatDate(transaction.date) }} · {{ getExpenseCategoryMeta(transaction.category).label }} ·
            {{ typeLabel(transaction.type) }}
          </small>
        </span>
        <strong class="transaction-amount" :class="String(transaction.type || '').toLowerCase()">
          {{ formatAmount(transaction) }}
        </strong>
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
        class="btn load-more-button"
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

.transaction-icon.coral { color: #ff796f; background: #fff0ed; }
.transaction-icon.green { color: #28b98a; background: #eafaf4; }
.transaction-icon.blue { color: #4f73e8; background: #edf2ff; }
.transaction-icon.purple { color: #8170ff; background: #f0edff; }
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
  border: 1px solid #8170ff;
  border-radius: 12px;
  color: #6b5bd2;
  background: #ffffff;
  font-weight: 700;
}

.load-more-button:hover:not(:disabled),
.load-more-button:focus:not(:disabled) {
  color: #ffffff;
  background: #8170ff;
}

@media (max-width: 575.98px) {
  .transaction-list li {
    grid-template-columns: 40px minmax(0, 1fr);
  }

  .transaction-amount {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
