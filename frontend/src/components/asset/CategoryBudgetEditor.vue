<script setup>
import { computed, reactive, ref, watch } from "vue";
import {
  BUDGET_CATEGORY_CODES,
  getExpenseCategoryMeta,
} from "@/features/financial/financialCategories";
import { formatNumber, formatWon } from "@/commonUtils/formatters";

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  budgetSummary: {
    type: Object,
    default: null,
  },
  targetMonth: {
    type: String,
    default: "",
  },
  isSaving: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["close", "save"]);

const totalInput = ref("");
const categoryInputs = reactive({});

const categoryItems = computed(() => BUDGET_CATEGORY_CODES.map((code) => {
  const meta = getExpenseCategoryMeta(code);
  const current = props.budgetSummary?.categories?.find(
    (category) => category.category === code,
  );

  return {
    code,
    label: meta.label,
    icon: meta.icon,
    color: meta.color,
    spentAmount: Number(current?.spentAmount) || 0,
  };
}));

const parseAmount = (value) => {
  const normalized = String(value ?? "").replaceAll(",", "").trim();
  if (!normalized) return 0;
  const amount = Number(normalized);
  return Number.isFinite(amount) && amount >= 0 ? amount : NaN;
};

const totalAmount = computed(() => parseAmount(totalInput.value));
const allocatedAmount = computed(() => categoryItems.value.reduce((sum, category) => {
  const amount = parseAmount(categoryInputs[category.code]);
  return Number.isFinite(amount) ? sum + amount : sum;
}, 0));
const unallocatedAmount = computed(() => totalAmount.value - allocatedAmount.value);
const isValid = computed(() => (
  Number.isFinite(totalAmount.value) &&
  totalAmount.value > 0 &&
  Number.isFinite(allocatedAmount.value) &&
  allocatedAmount.value <= totalAmount.value
));

const initializeForm = () => {
  totalInput.value = formatNumber(props.budgetSummary?.totalAmount ?? 0);

  for (const category of BUDGET_CATEGORY_CODES) {
    const current = props.budgetSummary?.categories?.find(
      (item) => item.category === category,
    );
    categoryInputs[category] = formatNumber(current?.budgetAmount ?? 0);
  }
};

watch(
  () => [props.visible, props.budgetSummary],
  initializeForm,
  { immediate: true, deep: true },
);

const formatInput = (key) => {
  const numericValue = String(key === "total" ? totalInput.value : categoryInputs[key])
    .replace(/[^0-9]/g, "");
  const formattedValue = numericValue ? formatNumber(numericValue) : "";

  if (key === "total") {
    totalInput.value = formattedValue;
  } else {
    categoryInputs[key] = formattedValue;
  }
};

const formatUsageRate = (rate) => {
  if (rate === null || rate === undefined) return "-";
  const numericRate = Number(rate);
  if (!Number.isFinite(numericRate)) return "-";
  return `${Number.isInteger(numericRate) ? numericRate : numericRate.toFixed(2)}%`;
};

const save = () => {
  if (!isValid.value) {
    alert("전체 예산은 0원보다 크고 카테고리 배분 합계 이상이어야 합니다.");
    return;
  }

  emit("save", {
    targetMonth: props.budgetSummary?.targetMonth || props.targetMonth,
    totalAmount: totalAmount.value,
    categoryBudgets: BUDGET_CATEGORY_CODES.map((category) => ({
      category,
      budgetAmount: parseAmount(categoryInputs[category]),
    })),
  });
};
</script>

<template>
  <div v-if="visible" class="budget-editor-layer">
    <div class="modal-backdrop fade show" @click="emit('close')"></div>
    <div
      class="modal fade show d-block"
      tabindex="-1"
      role="dialog"
      aria-modal="true"
      aria-labelledby="categoryBudgetEditorTitle"
      @keydown.esc="emit('close')"
    >
      <div class="modal-dialog modal-dialog-centered modal-lg modal-dialog-scrollable">
        <div class="modal-content category-budget-editor">
          <div class="modal-header">
            <div>
              <h2 id="categoryBudgetEditorTitle" class="modal-title h5 fw-bold mb-1">
                카테고리별 예산 수정
              </h2>
              <p class="text-secondary small mb-0">
                {{ targetMonth }}부터 매월 적용됩니다.
              </p>
            </div>
            <button
              type="button"
              class="btn-close"
              aria-label="닫기"
              @click="emit('close')"
            ></button>
          </div>

          <form @submit.prevent="save">
            <div class="modal-body">
              <div class="total-budget-input mb-4">
                <label for="categoryBudgetTotal" class="form-label fw-bold">전체 예산</label>
                <div class="input-group">
                  <input
                    id="categoryBudgetTotal"
                    v-model="totalInput"
                    type="text"
                    class="form-control"
                    inputmode="numeric"
                    autocomplete="off"
                    required
                    @input="formatInput('total')"
                  />
                  <span class="input-group-text">원</span>
                </div>
              </div>

              <div class="budget-allocation-summary rounded-3 p-3 mb-4">
                <div class="d-flex justify-content-between gap-3">
                  <span>카테고리 배분 합계</span>
                  <strong>{{ formatWon(allocatedAmount) }}</strong>
                </div>
                <div
                  class="d-flex justify-content-between gap-3 mt-2"
                  :class="{ 'text-danger': unallocatedAmount < 0 }"
                >
                  <span>기타·미배정 예산</span>
                  <strong>{{ formatWon(unallocatedAmount) }}</strong>
                </div>
              </div>

              <div class="category-budget-editor-grid">
                <div
                  v-for="category in categoryItems"
                  :key="category.code"
                  class="category-budget-editor-item"
                >
                  <div class="d-flex align-items-center justify-content-between gap-2 mb-2">
                    <label :for="`budget-${category.code}`" class="category-budget-label">
                      <span
                        class="category-budget-icon"
                        :style="{ color: category.color, backgroundColor: `${category.color}18` }"
                      >
                        <i :class="['bi', category.icon]" aria-hidden="true"></i>
                      </span>
                      {{ category.label }}
                    </label>
                    <small class="text-secondary">
                      지출 {{ formatWon(category.spentAmount) }}
                    </small>
                  </div>
                  <div class="input-group input-group-sm">
                    <input
                      :id="`budget-${category.code}`"
                      v-model="categoryInputs[category.code]"
                      type="text"
                      class="form-control"
                      inputmode="numeric"
                      autocomplete="off"
                      @input="formatInput(category.code)"
                    />
                    <span class="input-group-text">원</span>
                  </div>
                </div>
              </div>

              <p v-if="unallocatedAmount < 0" class="text-danger small mt-3 mb-0">
                카테고리 배분 합계가 전체 예산을 초과했습니다.
              </p>
              <p v-else class="text-secondary small mt-3 mb-0">
                배분하지 않은 금액은 기타·미배정 예산으로 자동 계산됩니다.
              </p>
            </div>

            <div class="modal-footer">
              <button type="button" class="btn btn-light" :disabled="isSaving" @click="emit('close')">
                취소
              </button>
              <button type="submit" class="btn btn-primary" :disabled="isSaving || !isValid">
                <span v-if="isSaving" class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>
                저장
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.budget-editor-layer {
  position: relative;
  z-index: 1060;
}

.category-budget-editor {
  overflow: hidden;
  border: 0;
  border-radius: 24px;
}

.category-budget-editor .modal-header,
.category-budget-editor .modal-footer {
  border-color: #edf0f5;
}

.budget-allocation-summary {
  color: #555b6e;
  background: #f7f6fc;
}

.category-budget-editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.category-budget-editor-item {
  padding: 14px;
  border: 1px solid #edf0f5;
  border-radius: 16px;
}

.category-budget-label {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: #343044;
  font-size: 0.9rem;
  font-weight: 700;
}

.category-budget-icon {
  display: inline-flex;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
}

@media (max-width: 575.98px) {
  .category-budget-editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
