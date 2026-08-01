<script setup>
defineProps({
  icon: {
    type: String,
    default: "bi-info-circle",
  },
  title: {
    type: String,
    required: true,
  },
  content: {
    type: String,
    default: "",
  },
})
</script>

<template>
  <section class="report-section mb-4">
    <h2 class="h6 fw-bold d-flex align-items-center gap-2 mb-2">
      <i class="bi" :class="icon" aria-hidden="true"></i>
      {{ title }}
    </h2>
    <p v-if="content" class="mb-0 report-section-content">{{ content }}</p>
    <p v-else class="mb-0 text-secondary fst-italic">아직 내용이 준비되지 않았습니다.</p>
  </section>
</template>

<style scoped>
.report-section-content {
  white-space: pre-line;
  line-height: 1.7;
}
</style>
