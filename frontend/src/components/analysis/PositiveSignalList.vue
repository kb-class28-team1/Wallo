<script setup>
import { categoryLabel, formatWon } from "@/types/consumptionAnalysis"

defineProps({
  positives: { type: Array, default: () => [] },
  streaks: { type: Array, default: () => [] },
})
</script>

<template>
  <div class="d-flex flex-column gap-2">
    <div v-for="item in positives" :key="`positive-${item.categoryCode}`" class="positive-row">
      <i class="bi bi-check-circle-fill text-success"></i>
      <span>
        <strong>{{ categoryLabel(item.categoryCode) }}</strong> 소비를
        {{ formatWon(item.decreaseAmount) }} 줄였어요
        <small class="text-secondary">({{ Number(item.decreaseRate).toFixed(1) }}%)</small>
      </span>
    </div>
    <div v-for="item in streaks" :key="item.kind" class="positive-row">
      <i class="bi bi-fire text-success"></i>
      <span>{{ item.label }}가 {{ item.streakCount }}개월 연속 개선됐어요.</span>
    </div>
  </div>
</template>

<style scoped>
.positive-row { display: flex; align-items: center; gap: 0.65rem; font-size: 0.9rem; }
</style>
