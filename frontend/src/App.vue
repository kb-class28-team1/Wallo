<script setup>
import { computed } from "vue"
import { RouterView, useRoute } from "vue-router"
import SideNavigation from "@/components/navigation/SideNavigation.vue"
import TopHeader from "@/components/navigation/TopHeader.vue"
import { useModalEnter } from "@/composables/useModalEnter"

const route = useRoute()
const usesAppShell = computed(() => Boolean(route.meta.appShell))
useModalEnter()
</script>

<template>
  <div v-if="usesAppShell" class="app-shell d-flex min-vh-100">
    <SideNavigation />
    <div class="app-shell-body d-flex flex-grow-1 flex-column">
      <TopHeader />
      <main class="page-content flex-grow-1">
        <div class="page-view">
          <RouterView />
        </div>
      </main>
    </div>
  </div>
  <RouterView v-else />
</template>

<style scoped>
.app-shell {
  min-width: 100%;
  background: #fafafa;
}

.app-shell-body {
  width: calc(100% - 273px);
  min-width: 0;
  min-height: 100vh;
  margin-left: 273px;
}

.page-content {
  display: flex;
  justify-content: center;
  min-width: 0;
  min-height: 100vh;
  padding: 100px 32px 32px;
  background: #fafafa;
}

.page-view {
  width: 100%;
  max-width: 1180px;
}

@media (max-width: 767.98px) {
  .page-content {
    padding-right: 20px;
    padding-left: 20px;
  }
}
</style>
