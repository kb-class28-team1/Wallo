<script setup>
import { computed } from "vue"
import { BUDGET_STATUS, formatWon } from "@/types/consumptionAnalysis"

const props = defineProps({ budget: { type: Object, required: true } })
const status = computed(() => BUDGET_STATUS[props.budget.status] || BUDGET_STATUS.NORMAL)
const width = computed(() => Math.min(100, Math.max(0, Number(props.budget.usageRate) || 0)))
</script>

<template>
  <div>
    <div class="d-flex justify-content-between align-items-center mb-2">
      <strong><i class="bi bi-speedometer2 me-2"></i>예산 사용 현황</strong>
      <span class="badge" :class="`text-bg-${status.variant}`">{{ status.label }}</span>
    </div>
    <div class="progress" role="progressbar" :aria-valuenow="width" aria-valuemin="0" aria-valuemax="100">
      <div class="progress-bar" :class="`bg-${status.variant}`" :style="{ width: `${width}%` }"></div>
    </div>
    <div class="d-flex justify-content-between mt-2 small text-secondary">
      <span>{{ formatWon(budget.spent) }} / {{ formatWon(budget.budgetAmount) }}</span>
      <span>사용 {{ Number(budget.usageRate).toFixed(1) }}%</span>
    </div>
    <small class="d-block mt-1 text-secondary">
      월 진행률보다 {{ Math.abs(Number(budget.gap)).toFixed(1) }}%p
      {{ Number(budget.gap) > 0 ? "빠르게" : "여유 있게" }} 사용 중이에요.
    </small>
  </div>
</template>
