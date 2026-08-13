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
});

const emit = defineEmits(["close", "save"]);

const selectedCategory = ref("");

const categoryOptions = computed(() => Object.entries(EXPENSE_CATEGORY_META).map(
  ([value, meta]) => ({ value, label: meta.label }),
));

const canSave = computed(() => Boolean(
  props.transaction?.transactionId && selectedCategory.value && !props.isSaving,
));

watch(
  [() => props.visible, () => props.transaction],
  ([visible, transaction]) => {
    if (visible && transaction) {
      selectedCategory.value = normalizeExpenseCategory(transaction.category);
    }
  },
  { immediate: true },
);

const close = () => {
  if (!props.isSaving) emit("close");
};

const save = () => {
  if (!canSave.value) return;

  emit("save", {
    transactionId: props.transaction.transactionId,
    category: selectedCategory.value,
  });
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
                카테고리 수정
              </h2>
              <p class="text-secondary small mb-0">
                {{ transaction?.merchantName || "거래내역" }}
              </p>
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
            <label for="expenseCategoryEditSelect" class="form-label fw-bold">
              카테고리
            </label>
            <select
              id="expenseCategoryEditSelect"
              v-model="selectedCategory"
              class="form-select"
              :disabled="isSaving"
            >
              <option
                v-for="option in categoryOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </div>

          <div class="modal-footer">
            <button
              type="button"
              class="btn btn-outline-secondary"
              :disabled="isSaving"
              @click="close"
            >
              취소
            </button>
            <button type="submit" class="btn btn-primary" :disabled="!canSave">
              <span
                v-if="isSaving"
                class="spinner-border spinner-border-sm me-2"
                aria-hidden="true"
              ></span>
              {{ isSaving ? "저장 중..." : "저장" }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </template>
</template>

<style scoped>
.category-edit-modal {
  overflow: hidden;
  border: 0;
  border-radius: 24px;
}

.category-edit-modal .modal-header,
.category-edit-modal .modal-footer {
  border-color: #edf0f5;
}

.category-edit-modal .modal-body {
  padding: 24px 28px;
}

.category-edit-modal .modal-footer {
  padding: 16px 28px 24px;
}
</style>
