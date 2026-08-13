<script setup>
import { computed, ref, watch } from "vue";
import {
  EXPENSE_CATEGORY_META,
  normalizeExpenseCategory,
} from "@/features/financial/financialCategories";

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  transaction: {
    type: Object,
    default: null,
  },
  isSaving: {
    type: Boolean,
    default: false,
  },
  mode: {
    type: String,
    default: "edit",
  },
  initialCategory: {
    type: String,
    default: "ALL",
  },
});

const emit = defineEmits(["close", "save"]);

const CATEGORY_GROUPS = Object.freeze({
  EXPENSE: [
    "FOOD",
    "CAFE",
    "TRANSPORT",
    "SHOPPING",
    "DELIVERY",
    "HOUSING",
    "LIVING",
    "CULTURE",
    "HEALTH",
    "EDUCATION",
    "LOAN_REPAYMENT",
    "ETC",
  ],
  INCOME: ["INCOME"],
  TRANSFER: ["SEND"],
});

const TYPE_TABS = Object.freeze([
  { value: "INCOME", label: "수입" },
  { value: "EXPENSE", label: "지출" },
  { value: "TRANSFER", label: "이체" },
]);

const selectedCategory = ref("");
const selectedType = ref("EXPENSE");

const isFilterMode = computed(() => props.mode === "filter");

const categoryValues = (type) => [
  ...(isFilterMode.value && type === "EXPENSE" ? ["ALL"] : []),
  ...CATEGORY_GROUPS[type],
];

const categoryOptions = computed(() => categoryValues(selectedType.value).map((value) => {
  if (value === "ALL") {
    return {
      value,
      label: "전체",
      icon: "bi-grid-3x3-gap",
      colorClass: "gray",
    };
  }

  const meta = EXPENSE_CATEGORY_META[value];
  return {
    value,
    label: meta.label,
    icon: meta.icon,
    colorClass: meta.colorClass,
  };
}));

const canSave = computed(() => Boolean(
  (isFilterMode.value || props.transaction?.transactionId) &&
  selectedCategory.value &&
  !props.isSaving,
));

const normalizeTransactionType = (type) => {
  if (type === "INCOME" || type === "TRANSFER") return type;
  return "EXPENSE";
};

const selectType = (type) => {
  selectedType.value = type;
  const nextOptions = categoryValues(type);
  if (!nextOptions.includes(selectedCategory.value)) {
    selectedCategory.value = nextOptions[0];
  }
};

const initialCategory = (transaction) => {
  if (isFilterMode.value) {
    return props.initialCategory === "ALL"
      ? "ALL"
      : normalizeExpenseCategory(props.initialCategory);
  }

  return normalizeExpenseCategory(transaction?.category);
};

watch(
  [() => props.visible, () => props.transaction, () => props.initialCategory, () => props.mode],
  ([visible, transaction]) => {
    if (visible && transaction) {
      selectedType.value = normalizeTransactionType(transaction.type);
    } else if (visible && isFilterMode.value) {
      const category = initialCategory(transaction);
      selectedType.value = category === "INCOME"
        ? "INCOME"
        : category === "SEND"
          ? "TRANSFER"
          : "EXPENSE";
    }

    if (visible) {
      const category = initialCategory(transaction);
      selectedCategory.value = categoryValues(selectedType.value).includes(category)
        ? category
        : categoryValues(selectedType.value)[0];
    }
  },
  { immediate: true },
);

const close = () => {
  if (!props.isSaving) emit("close");
};

const save = () => {
  if (!canSave.value) return;

  emit(
    "save",
    isFilterMode.value
      ? { category: selectedCategory.value }
      : {
          transactionId: props.transaction.transactionId,
          category: selectedCategory.value,
        },
  );
};
</script>

<template>
  <template v-if="visible">
    <div class="modal-backdrop fade show" @click="close"></div>
    <div
      class="modal fade show d-block"
      tabindex="-1"
      role="dialog"
      aria-modal="true"
      aria-labelledby="expenseCategoryEditModalTitle"
      @keydown.esc="close"
    >
      <div class="modal-dialog modal-dialog-centered">
        <form class="modal-content category-edit-modal" @submit.prevent="save">
          <div class="modal-header">
            <div>
              <h2 id="expenseCategoryEditModalTitle" class="modal-title h5 fw-bold mb-1">
                {{ isFilterMode ? "카테고리 필터" : "카테고리 선택" }}
              </h2>
            </div>
            <button
              type="button"
              class="btn-close"
              aria-label="닫기"
              :disabled="isSaving"
              @click="close"
            ></button>
          </div>

          <div class="modal-body">
            <div class="category-type-tabs" role="tablist" aria-label="거래 유형">
              <button
                v-for="tab in TYPE_TABS"
                :key="tab.value"
                type="button"
                class="category-type-tab"
                :class="{ active: selectedType === tab.value }"
                :data-testid="`transaction-type-${tab.value}`"
                role="tab"
                :aria-selected="selectedType === tab.value"
                :disabled="isSaving"
                @click="selectType(tab.value)"
              >
                {{ tab.label }}
              </button>
            </div>

            <div
              class="category-option-grid"
              role="listbox"
              aria-label="카테고리 목록"
            >
              <button
                v-for="option in categoryOptions"
                :key="option.value"
                type="button"
                class="category-option"
                :class="{ selected: selectedCategory === option.value }"
                :data-testid="`category-option-${option.value}`"
                role="option"
                :aria-selected="selectedCategory === option.value"
                :disabled="isSaving"
                @click="selectedCategory = option.value"
              >
                <span class="category-option-icon" :class="option.colorClass">
                  <i :class="['bi', option.icon]" aria-hidden="true"></i>
                </span>
                <span class="category-option-label">{{ option.label }}</span>
              </button>
            </div>
          </div>

          <div class="modal-footer">
            <button type="submit" class="btn category-save-button" :disabled="!canSave">
              <span
                v-if="isSaving"
                class="spinner-border spinner-border-sm me-2"
                aria-hidden="true"
              ></span>
              {{ isSaving ? "처리 중..." : isFilterMode ? "적용" : "확인" }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </template>
</template>

<style scoped>
.category-edit-modal {
  max-height: calc(100vh - 32px);
  overflow: hidden;
  border: 0;
  border-radius: 24px;
}

.category-edit-modal .modal-header,
.category-edit-modal .modal-footer {
  border-color: #edf0f5;
}

.category-edit-modal .modal-body {
  overflow-y: auto;
  padding: 20px 28px 28px;
}

.category-edit-modal .modal-footer {
  padding: 16px 28px 24px;
}

.category-type-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
  margin-bottom: 24px;
  padding: 6px;
  border-radius: 28px;
  background: #f1f3f7;
}

.category-type-tab {
  min-height: 48px;
  border: 0;
  border-radius: 24px;
  color: #a1a8b7;
  background: transparent;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.category-type-tab.active {
  color: #343044;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(49, 54, 74, 0.05);
}

.category-type-tab:focus-visible {
  outline: 2px solid #6b5bd2;
  outline-offset: 2px;
}

.category-type-tab:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.category-option-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.category-option {
  display: flex;
  min-height: 112px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px 8px;
  border: 2px solid #edf0f5;
  border-radius: 18px;
  color: #656b7c;
  background: #ffffff;
  font: inherit;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.category-option:hover,
.category-option:focus-visible {
  border-color: #b9b0f2;
  box-shadow: 0 8px 18px rgba(80, 67, 170, 0.1);
  transform: translateY(-1px);
}

.category-option:focus-visible {
  outline: 2px solid #6b5bd2;
  outline-offset: 2px;
}

.category-option.selected {
  border-color: #6b5bd2;
  color: #343044;
  box-shadow: 0 8px 18px rgba(107, 91, 210, 0.12);
}

.category-option:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.category-option-icon {
  display: inline-flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 15px;
  font-size: 1.35rem;
}

.category-option-icon.coral { color: #ff796f; background: #fff0ed; }
.category-option-icon.green { color: #28b98a; background: #eafaf4; }
.category-option-icon.blue { color: #4f73e8; background: #edf2ff; }
.category-option-icon.purple { color: #8170ff; background: #f0edff; }
.category-option-icon.gray { color: #7c8294; background: #f1f3f6; }

.category-option-label {
  overflow: hidden;
  max-width: 100%;
  font-size: 0.86rem;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-save-button {
  width: 100%;
  border: 0;
  border-radius: 14px;
  color: #ffffff;
  background: #10ae76;
  font-weight: 800;
}

.category-save-button:hover:not(:disabled),
.category-save-button:focus-visible {
  color: #ffffff;
  background: #0b9765;
}

.category-save-button:disabled {
  color: #ffffff;
  background: #9ad8bf;
}

@media (max-width: 575.98px) {
  .category-edit-modal {
    max-height: 100vh;
    border-radius: 28px 28px 0 0;
  }

  .modal-dialog {
    align-items: flex-end;
    min-height: 100%;
    margin: 0;
  }

  .category-option-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
  }

  .category-option {
    min-height: 96px;
    border-radius: 15px;
  }
}
</style>
