<script setup>
import { computed } from "vue"
import { BUDGET_STATUS, formatWon } from "@/types/consumptionAnalysis"

const props = defineProps({ budget: { type: Object, required: true } })
const status = computed(() => BUDGET_STATUS[props.budget.status] || BUDGET_STATUS.NORMAL)
const width = computed(() => Math.min(100, Math.max(0, Number(props.budget.usageRate) || 0)))
const recommendedSpend = computed(() => {
  const budgetAmount = Number(props.budget.budgetAmount)
  const monthProgress = Number(props.budget.monthProgress)
  if (!Number.isFinite(budgetAmount) || !Number.isFinite(monthProgress)) return null
  return budgetAmount * monthProgress / 100
})
const spendingGap = computed(() => {
  const spent = Number(props.budget.spent)
  if (!Number.isFinite(spent) || recommendedSpend.value === null) return null
  return spent - recommendedSpend.value
})
const spendingGapAmount = computed(() => (
  spendingGap.value === null ? 0 : Math.round(Math.abs(spendingGap.value))
))
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
      <span>{{ Number(budget.usageRate).toFixed(1) }}% 사용</span>
    </div>
    <small v-if="spendingGap !== null" class="d-block mt-1 text-secondary">
      <template v-if="spendingGap > 0">
        권장 지출액보다
        <span class="text-danger">{{ formatWon(spendingGapAmount) }}</span>
        더 쓰고 있어요.
      </template>
      <template v-else-if="spendingGap < 0">
        권장 지출액보다 {{ formatWon(spendingGapAmount) }} 덜 쓰고 있어요.
      </template>
      <template v-else>권장 지출액과 비슷하게 쓰고 있어요.</template>
    </small>
  </div>
</template>
