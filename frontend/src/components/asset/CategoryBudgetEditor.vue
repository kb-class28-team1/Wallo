<script setup>
import { computed, reactive, ref, watch } from "vue"
import {
  BUDGET_CATEGORY_CODES,
  getExpenseCategoryMeta,
} from "@/features/financial/financialCategories"
import { formatNumber, formatWon } from "@/utils/formatters"
import AppDialog from "@/components/common/AppDialog.vue"

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
})

const emit = defineEmits(["close", "save"])

const totalInput = ref("")
const categoryInputs = reactive({})

const categoryItems = computed(() =>
  BUDGET_CATEGORY_CODES.map((code) => {
    const meta = getExpenseCategoryMeta(code)
    const current = props.budgetSummary?.categories?.find((category) => category.category === code)

    return {
      code,
      label: meta.label,
      icon: meta.icon,
      color: meta.color,
      spentAmount: Number(current?.spentAmount) || 0,
    }
  }),
)

const parseAmount = (value) => {
  const normalized = String(value ?? "")
    .replaceAll(",", "")
    .trim()
  if (!normalized) return 0
  const amount = Number(normalized)
  return Number.isFinite(amount) && amount >= 0 ? amount : NaN
}

const totalAmount = computed(() => parseAmount(totalInput.value))
const allocatedAmount = computed(() =>
  categoryItems.value.reduce((sum, category) => {
    const amount = parseAmount(categoryInputs[category.code])
    return Number.isFinite(amount) ? sum + amount : sum
  }, 0),
)
const unallocatedAmount = computed(() => totalAmount.value - allocatedAmount.value)
const isValid = computed(
  () =>
    Number.isFinite(totalAmount.value) &&
    totalAmount.value > 0 &&
    Number.isFinite(allocatedAmount.value) &&
    allocatedAmount.value <= totalAmount.value,
)

const syncTotalToCategoryAllocation = () => {
  totalInput.value = formatNumber(allocatedAmount.value)
}

const initializeForm = () => {
  totalInput.value = formatNumber(props.budgetSummary?.totalAmount ?? 0)

  for (const category of BUDGET_CATEGORY_CODES) {
    const current = props.budgetSummary?.categories?.find((item) => item.category === category)
    categoryInputs[category] = formatNumber(current?.budgetAmount ?? 0)
  }
}

watch(() => [props.visible, props.budgetSummary], initializeForm, { immediate: true, deep: true })

const formatInput = (key) => {
  const numericValue = String(key === "total" ? totalInput.value : categoryInputs[key]).replace(
    /[^0-9]/g,
    "",
  )
  const formattedValue = numericValue ? formatNumber(numericValue) : ""

  if (key === "total") {
    totalInput.value = formattedValue
  } else {
    categoryInputs[key] = formattedValue
    syncTotalToCategoryAllocation()
  }
}

const formatUsageRate = (rate) => {
  if (rate === null || rate === undefined) return "-"
  const numericRate = Number(rate)
  if (!Number.isFinite(numericRate)) return "-"
  return `${Number.isInteger(numericRate) ? numericRate : numericRate.toFixed(2)}%`
}

const save = () => {
  if (!isValid.value) {
    alert("전체 예산은 0원보다 크고 카테고리 배분 합계 이상이어야 합니다.")
    return
  }

  emit("save", {
    targetMonth: props.budgetSummary?.targetMonth || props.targetMonth,
    totalAmount: totalAmount.value,
    categoryBudgets: BUDGET_CATEGORY_CODES.map((category) => ({
      category,
      budgetAmount: parseAmount(categoryInputs[category]),
    })),
  })
}
</script>

<template>
  <AppDialog
    :visible="visible"
    title="카테고리별 예산 수정"
    message=""
    size="lg"
    show-cancel
    cancel-text="취소"
    :confirm-text="isSaving ? '저장 중...' : '확인'"
    confirm-variant="primary"
    :confirm-disabled="isSaving || !isValid"
    :confirm-loading="isSaving"
    :close-on-backdrop="false"
    @close="emit('close')"
    @confirm="save"
  >
    <form class="category-budget-editor-form" @submit.prevent="save">
      <div class="category-budget-editor-body">
        <p class="budget-editor-description text-secondary small mb-4">
          {{ targetMonth }}부터 매월 적용됩니다.
        </p>
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
          <p class="text-secondary small mt-2 mb-0">
            카테고리 금액을 입력하면 전체 예산이 배분 합계로 자동 계산됩니다. 전체 예산은 직접
            수정할 수 있습니다.
          </p>
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
            <span>미배분 예산</span>
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
              <small class="text-secondary"> 지출 {{ formatWon(category.spentAmount) }} </small>
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
          배분하지 않은 금액은 미배분 예산으로 자동 계산됩니다.
        </p>
      </div>
    </form>
  </AppDialog>
</template>

<style scoped>
:deep(.app-dialog--lg) {
  max-width: 760px;
  padding: 0;
}

:deep(.app-dialog-header) {
  padding: var(--wallo-space-5) var(--wallo-space-6);
  border-bottom: 1px solid var(--wallo-color-border-soft);
}

:deep(.app-dialog-body) {
  max-height: calc(100vh - 230px);
  overflow-y: auto;
  margin: 0;
  padding: var(--wallo-space-5) var(--wallo-space-6);
}

:deep(.app-dialog-footer) {
  padding: var(--wallo-space-4) var(--wallo-space-6) var(--wallo-space-5);
  border-top: 1px solid var(--wallo-color-border-soft);
}

.category-budget-editor-form {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
}

.category-budget-editor-body {
  min-height: 0;
  overflow-y: auto;
}

.budget-editor-description {
  margin-top: 0;
}

.budget-allocation-summary {
  color: var(--wallo-color-text-muted);
  background: var(--wallo-color-surface-soft);
}

.category-budget-editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.category-budget-editor-item {
  padding: 14px;
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: var(--wallo-radius-md);
}

.category-budget-label {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: var(--wallo-color-text);
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
  :deep(.app-dialog--lg) {
    max-width: 100%;
  }

  :deep(.app-dialog-header),
  :deep(.app-dialog-body),
  :deep(.app-dialog-footer) {
    padding-right: var(--wallo-space-4);
    padding-left: var(--wallo-space-4);
  }

  .category-budget-editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
