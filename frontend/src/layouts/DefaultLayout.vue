<script setup>
import { RouterView } from 'vue-router'
import SideNavigation from '@/components/navigation/SideNavigation.vue'
import TopHeader from '@/components/navigation/TopHeader.vue'
</script>

<template>
  <div class="default-layout d-flex min-vh-100">
    <SideNavigation />
    <div class="layout-body d-flex flex-grow-1 flex-column">
      <TopHeader />
      <main class="page-content flex-grow-1">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.default-layout {
  min-width: 100%;
}

.layout-body {
  min-width: 0;
  margin-left: 273px;
}

.page-content {
  min-width: 0;
  background: #fafafa;
}
</style>
