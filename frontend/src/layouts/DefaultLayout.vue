<script setup>
import { RouterView } from 'vue-router'
import SideNavigation from '@/components/navigation/SideNavigation.vue'
</script>

<template>
  <div class="default-layout d-flex min-vh-100">
    <SideNavigation />
    <main class="page-content flex-grow-1">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.default-layout {
  min-width: 100%;
}

.page-content {
  min-width: 0;
  background: #fafafa;
}
</style>
