<script setup>
import { computed } from "vue"
import { TIME_SLOT_LABELS, WEEKDAY_LABELS, formatWon } from "@/types/consumptionAnalysis"

const props = defineProps({ pattern: { type: Object, required: true } })
const icon = computed(() => ({
  WEEKDAY: "bi-calendar-week",
  WEEKEND: "bi-sun",
  TIME_OF_DAY: "bi-clock",
})[props.pattern.type] || "bi-arrow-repeat")
const description = computed(() => {
  const p = props.pattern
  if (p.type === "WEEKDAY") {
    return {
      type: "WEEKDAY",
      highlight: WEEKDAY_LABELS[p.weekday] || "같은 요일",
    }
  }
  if (p.type === "WEEKEND") {
    return {
      type: "WEEKEND",
      weekendAvg: formatWon(p.weekendAvg),
      weekdayAvg: formatWon(p.weekdayAvg),
    }
  }
  return {
    type: "TIME_OF_DAY",
    highlight: TIME_SLOT_LABELS[p.timeSlot] || p.timeSlot,
  }
})
</script>

<template>
  <div class="analysis-subcard">
    <i class="bi me-2 text-primary" :class="icon" aria-hidden="true"></i>
    <span v-if="description.type === 'WEEKDAY'">
      최근 4주 동안
      <strong class="text-primary fw-semibold">{{ description.highlight }}</strong>
      소비가 가장 많았어요.
    </span>
    <span v-else-if="description.type === 'WEEKEND'">
      최근 4주 동안
      <strong class="text-primary fw-semibold">주말</strong>
      일평균 {{ description.weekendAvg }}으로 평일 {{ description.weekdayAvg }}보다 높아요.
    </span>
    <span v-else>
      최근 4주 동안
      <strong class="text-primary fw-semibold">{{ description.highlight }}</strong>
      소비가 집중됐어요.
    </span>
  </div>
</template>

<style scoped>
.analysis-subcard {
  padding: 0.75rem 0.9rem;
  background: var(--wallo-color-surface-soft);
  border-radius: 0.8rem;
  font-size: 0.9rem;
}
</style>
