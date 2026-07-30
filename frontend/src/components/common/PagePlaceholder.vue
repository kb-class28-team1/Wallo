<script setup>
defineProps({
  title: {
    type: String,
    required: true,
  },
})
</script>

<template>
  <section class="placeholder-page d-flex align-items-center justify-content-center">
    <div class="placeholder-card card w-100 border-0 text-center shadow-sm">
      <div class="card-body d-flex align-items-center justify-content-center p-5">
        <h1 class="mb-0 fw-bold">{{ title }}</h1>
      </div>
    </div>
  </section>
</template>

<style scoped>
.placeholder-page {
  min-height: calc(100vh - 132px);
}

.placeholder-card {
  min-height: 420px;
  border-radius: 28px;
}
</style>
