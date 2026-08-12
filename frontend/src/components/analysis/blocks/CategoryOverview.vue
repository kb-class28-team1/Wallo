<script setup>
import { computed } from "vue"
import { categoryLabel, formatWon } from "@/types/consumptionAnalysis"

const props = defineProps({
  categories: { type: Array, default: () => [] },
  repeatingCategories: { type: Array, default: () => [] },
})

const sortedCategories = computed(() => [...props.categories].sort((left, right) =>
  Number(right.current || 0) - Number(left.current || 0)
    || String(left.categoryCode || "").localeCompare(String(right.categoryCode || "")),
))

const rateText = (item) => {
  if (item.deltaRate === null || item.deltaRate === undefined) return "비교 기간 지출 없음"
  const direction = Number(item.deltaRate) >= 0 ? "증가" : "감소"
  return `${Math.abs(Number(item.deltaRate)).toFixed(1)}% ${direction}`
}

const isRepeating = (code, repeatingCategories) =>
  repeatingCategories.some((item) => item.categoryCode === code)
</script>

<template>
  <div class="d-flex flex-column">
    <div v-for="item in sortedCategories" :key="item.categoryCode" class="category-row">
      <div>
        <strong>{{ categoryLabel(item.categoryCode) }}</strong>
        <small class="d-block text-secondary">
          {{ item.transactionCount }}건 · 이전 {{ formatWon(item.previous) }} · {{ rateText(item) }}
        </small>
      </div>
      <div class="text-end">
        <strong>{{ formatWon(item.current) }}</strong>
        <span
          v-if="isRepeating(item.categoryCode, repeatingCategories)"
          class="badge text-bg-primary-subtle text-primary-emphasis d-block mt-1"
        >반복 소비</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.category-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.65rem 0;
  border-bottom: 1px solid #ece9f3;
}
.category-row:last-child { border-bottom: 0; }
</style>
