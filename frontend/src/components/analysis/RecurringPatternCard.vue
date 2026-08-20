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
    return `최근 완료된 4주 중 ${p.repeatWeeks}주에서 ${WEEKDAY_LABELS[p.weekday] || "같은 요일"} 소비가 가장 높았어요.`
  }
  if (p.type === "WEEKEND") {
    return `주말 일평균 ${formatWon(p.weekendAvg)}으로 평일 ${formatWon(p.weekdayAvg)}보다 높아요.`
  }
  return `최근 완료된 4주 중 ${p.repeatWeeks}주에서 ${TIME_SLOT_LABELS[p.timeSlot] || p.timeSlot} 소비가 집중됐어요.`
})
</script>

<template>
  <div class="analysis-subcard">
    <i class="bi me-2 text-primary" :class="icon" aria-hidden="true"></i>
    <span>{{ description }}</span>
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
