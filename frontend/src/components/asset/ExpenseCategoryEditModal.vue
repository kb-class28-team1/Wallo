<script setup>
import { computed, ref, watch } from "vue"
import {
  EXPENSE_CATEGORY_META,
  normalizeExpenseCategory,
} from "@/features/financial/financialCategories"
import AppDialog from "@/components/common/AppDialog.vue"
import AppTabs from "@/components/ui/AppTabs.vue"

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
})

const emit = defineEmits(["close", "save"])

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
})

const TYPE_TABS = Object.freeze([
  { value: "INCOME", label: "수입" },
  { value: "EXPENSE", label: "지출" },
  { value: "TRANSFER", label: "이체" },
])

const selectedCategory = ref("")
const selectedType = ref("EXPENSE")
const typeTabs = computed(() => TYPE_TABS.map((tab) => ({ ...tab, disabled: props.isSaving })))

const isFilterMode = computed(() => props.mode === "filter")

const categoryValues = (type) => [
  ...(isFilterMode.value && type === "EXPENSE" ? ["ALL"] : []),
  ...CATEGORY_GROUPS[type],
]

const categoryOptions = computed(() =>
  categoryValues(selectedType.value).map((value) => {
    if (value === "ALL") {
      return {
        value,
        label: "전체",
        icon: "bi-grid-3x3-gap",
        colorClass: "gray",
      }
    }

    const meta = EXPENSE_CATEGORY_META[value]
    return {
      value,
      label: meta.label,
      icon: meta.icon,
      colorClass: meta.colorClass,
    }
  }),
)

const canSave = computed(() =>
  Boolean(
    (isFilterMode.value || props.transaction?.transactionId) &&
    selectedCategory.value &&
    !props.isSaving,
  ),
)

const normalizeTransactionType = (type) => {
  if (type === "INCOME" || type === "TRANSFER") return type
  return "EXPENSE"
}

const selectType = (type) => {
  selectedType.value = type
  const nextOptions = categoryValues(type)
  if (!nextOptions.includes(selectedCategory.value)) {
    selectedCategory.value = nextOptions[0]
  }
}

const initialCategory = (transaction) => {
  if (isFilterMode.value) {
    return props.initialCategory === "ALL" ? "ALL" : normalizeExpenseCategory(props.initialCategory)
  }

  return normalizeExpenseCategory(transaction?.category)
}

watch(
  [() => props.visible, () => props.transaction, () => props.initialCategory, () => props.mode],
  ([visible, transaction]) => {
    if (visible && transaction) {
      selectedType.value = normalizeTransactionType(transaction.type)
    } else if (visible && isFilterMode.value) {
      const category = initialCategory(transaction)
      selectedType.value =
        category === "INCOME" ? "INCOME" : category === "SEND" ? "TRANSFER" : "EXPENSE"
    }

    if (visible) {
      const category = initialCategory(transaction)
      selectedCategory.value = categoryValues(selectedType.value).includes(category)
        ? category
        : categoryValues(selectedType.value)[0]
    }
  },
  { immediate: true },
)

const close = () => {
  if (!props.isSaving) emit("close")
}

const save = () => {
  if (!canSave.value) return

  emit(
    "save",
    isFilterMode.value
      ? { category: selectedCategory.value }
      : {
          transactionId: props.transaction.transactionId,
          category: selectedCategory.value,
        },
  )
}
</script>

<template>
  <AppDialog
    :visible="visible"
    :title="isFilterMode ? '카테고리 필터' : '카테고리 선택'"
    size="lg"
    :confirm-text="isFilterMode ? '적용' : '확인'"
    confirm-variant="primary"
    :confirm-disabled="!canSave"
    :confirm-loading="isSaving"
    @close="close"
    @confirm="save"
  >
    <div class="category-edit-modal-body">
      <AppTabs
        v-model="selectedType"
        :items="typeTabs"
        variant="segment"
        full-width
        aria-label="거래 유형"
        @update:model-value="selectType"
      >
        <template #tab="{ item }">
          <span :data-testid="`transaction-type-${item.value}`">{{ item.label }}</span>
        </template>
      </AppTabs>

      <div class="category-option-grid" role="listbox" aria-label="카테고리 목록">
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
  </AppDialog>
</template>

<style scoped>
:deep(.app-dialog--lg) {
  max-width: 680px;
  padding: 0;
}

:deep(.app-dialog-header) {
  padding: var(--wallo-space-5) var(--wallo-space-6);
  border-bottom: 1px solid var(--wallo-color-border-soft);
}

:deep(.app-dialog-body) {
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  margin: 0;
  padding: var(--wallo-space-5) var(--wallo-space-6);
}

:deep(.app-dialog-footer) {
  padding: var(--wallo-space-4) var(--wallo-space-6) var(--wallo-space-5);
  border-top: 1px solid var(--wallo-color-border-soft);
}

.category-edit-modal-body {
  min-width: 0;
}

.category-edit-modal-body :deep(.app-tabs__list) {
  margin-bottom: var(--wallo-space-5);
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
  border: 2px solid var(--wallo-color-border-soft);
  border-radius: var(--wallo-radius-lg);
  color: var(--wallo-color-text-muted);
  background: var(--wallo-color-surface);
  font: inherit;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    transform 0.16s ease;
}

.category-option:hover,
.category-option:focus-visible {
  border-color: color-mix(in srgb, var(--wallo-color-primary) 45%, var(--wallo-color-border));
  box-shadow: var(--wallo-shadow-card);
  transform: translateY(-1px);
}

.category-option:focus-visible {
  outline: 2px solid var(--wallo-color-primary);
  outline-offset: 2px;
}

.category-option.selected {
  border-color: var(--wallo-color-primary);
  color: var(--wallo-color-text);
  box-shadow: var(--wallo-shadow-card);
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

.category-option-icon.coral {
  color: #ff796f;
  background: #fff0ed;
}
.category-option-icon.green {
  color: #28b98a;
  background: #eafaf4;
}
.category-option-icon.blue {
  color: #4f73e8;
  background: #edf2ff;
}
.category-option-icon.purple {
  color: #8170ff;
  background: #f0edff;
}
.category-option-icon.gray {
  color: #7c8294;
  background: #f1f3f6;
}

.category-option-label {
  overflow: hidden;
  max-width: 100%;
  font-size: 0.86rem;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 575.98px) {
  :deep(.app-dialog--lg) {
    max-width: 100%;
    border-radius: var(--wallo-radius-lg);
  }

  :deep(.app-dialog-header),
  :deep(.app-dialog-body),
  :deep(.app-dialog-footer) {
    padding-right: var(--wallo-space-4);
    padding-left: var(--wallo-space-4);
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
