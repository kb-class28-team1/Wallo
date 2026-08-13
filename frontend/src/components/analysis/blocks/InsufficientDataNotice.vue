<script setup>
import { formatWon } from "@/types/consumptionAnalysis"

defineProps({ info: { type: Object, default: null } })
</script>

<template>
  <div class="alert alert-light border mb-0 rounded-4" role="status">
    <div class="d-flex gap-3">
      <i class="bi bi-hourglass-split fs-4 text-secondary" aria-hidden="true"></i>
      <div>
        <strong class="d-block mb-1">분석할 내역이 조금 더 필요해요</strong>
        <p class="mb-1 small text-secondary">{{ info?.message }}</p>
        <small class="text-secondary">
          현재 {{ info?.txCount ?? 0 }}건 · {{ formatWon(info?.totalAmount) }}
        </small>
      </div>
    </div>
  </div>
</template>
