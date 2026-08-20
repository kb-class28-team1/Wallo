<script setup>
import { categoryLabel, formatWon } from "@/types/consumptionAnalysis"

defineProps({
  spikes: { type: Array, default: () => [] },
  newSpendings: { type: Array, default: () => [] },
  oneTimeLarge: { type: Array, default: () => [] },
})
</script>

<template>
  <div class="d-flex flex-column gap-3">
    <div v-if="spikes.length">
      <strong class="d-block mb-2"><i class="bi bi-graph-up-arrow me-2"></i>증가한 소비</strong>
      <div v-for="item in spikes" :key="`spike-${item.categoryCode}`" class="signal-row">
        <span>{{ categoryLabel(item.categoryCode) }}</span>
        <span class="text-danger fw-semibold">+{{ formatWon(item.deltaAmount) }}</span>
      </div>
    </div>
    <div v-if="newSpendings.length">
      <strong class="d-block mb-2"><i class="bi bi-stars me-2"></i>새로 생긴 소비</strong>
      <div v-for="item in newSpendings" :key="`new-${item.categoryCode}`" class="signal-row">
        <span>{{ categoryLabel(item.categoryCode) }} · {{ item.txCount }}건</span>
        <span class="fw-semibold">{{ formatWon(item.current) }}</span>
      </div>
    </div>
    <div v-if="oneTimeLarge.length">
      <strong class="d-block mb-2"><i class="bi bi-receipt me-2"></i>단발성 고액 소비</strong>
      <div v-for="item in oneTimeLarge" :key="`large-${item.categoryCode}`" class="signal-row">
        <span>{{ categoryLabel(item.categoryCode) }}</span>
        <span>
          {{ formatWon(item.categoryAmount) }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.signal-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.45rem 0;
  border-bottom: 1px solid var(--wallo-color-border-soft);
  font-size: 0.9rem;
}
</style>
