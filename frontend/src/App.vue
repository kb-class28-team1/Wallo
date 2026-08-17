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
  background: var(--wallo-color-bg);
}

.app-shell-body {
  width: calc(100% - var(--wallo-sidebar-width));
  min-width: 0;
  min-height: 100vh;
  margin-left: var(--wallo-sidebar-width);
}

.page-content {
  display: flex;
  justify-content: center;
  min-width: 0;
  min-height: 100vh;
  padding: var(--wallo-page-top-offset) var(--wallo-page-gutter) var(--wallo-page-gutter);
  background: var(--wallo-color-bg);
}

.page-view {
  width: 100%;
  max-width: var(--wallo-content-max-width);
}

@media (max-width: 767.98px) {
  .page-content {
    padding-right: var(--wallo-page-gutter-mobile);
    padding-left: var(--wallo-page-gutter-mobile);
  }
}
</style>
