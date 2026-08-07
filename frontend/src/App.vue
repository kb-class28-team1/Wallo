<script setup>
import { computed, ref } from "vue"
import { RouterView, useRoute } from "vue-router"
import SideNavigation from "@/components/navigation/SideNavigation.vue"
import TopHeader from "@/components/navigation/TopHeader.vue"
import { useModalEnter } from "@/composables/useModalEnter"
import LandingView from "@/views/auth/LandingView.vue"
import router from "@/router"

const route = useRoute()
const usesAppShell = computed(() => Boolean(route.meta.appShell))
const isRouteLoading = ref(false)
let routeLoadingStartedAt = 0
let routeLoadingHideTimer

const startRouteLoading = () => {
  routeLoadingStartedAt = performance.now()
  isRouteLoading.value = true

  if (routeLoadingHideTimer) {
    window.clearTimeout(routeLoadingHideTimer)
    routeLoadingHideTimer = undefined
  }
}

const finishRouteLoading = () => {
  const elapsed = performance.now() - routeLoadingStartedAt
  const remaining = Math.max(0, 1000 - elapsed)

  routeLoadingHideTimer = window.setTimeout(() => {
    isRouteLoading.value = false
    routeLoadingHideTimer = undefined
  }, remaining)
}

const cancelRouteLoading = () => {
  if (routeLoadingHideTimer) {
    window.clearTimeout(routeLoadingHideTimer)
    routeLoadingHideTimer = undefined
  }

  isRouteLoading.value = false
}

router.beforeEach((to, from) => {
  if (from.name === "landing" && to.name === "login") {
    cancelRouteLoading()
    return
  }

  startRouteLoading()
})

router.afterEach(() => {
  finishRouteLoading()
})

router.onError(() => {
  finishRouteLoading()
})

useModalEnter()
</script>

<template>
  <div v-if="isRouteLoading" class="route-loading-overlay">
    <LandingView />
  </div>

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
.route-loading-overlay {
  position: fixed;
  z-index: 2000;
  inset: 0;
}

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
