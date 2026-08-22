<script setup>
import { computed, onBeforeUnmount, watch } from "vue"
import { RouterView, useRoute } from "vue-router"
import SideNavigation from "@/components/navigation/SideNavigation.vue"
import TopHeader from "@/components/navigation/TopHeader.vue"
import AppToast from "@/components/common/AppToast.vue"
import PointEarnedNotice from "@/components/common/PointEarnedNotice.vue"
import { useModalEnter } from "@/composables/useModalEnter"
import { useMissionStore } from "@/stores/missionStore"

const route = useRoute()
const missionStore = useMissionStore()
const usesAppShell = computed(() => Boolean(route.meta.appShell))
const getRouteViewKey = (viewRoute) =>
  viewRoute.matched.length > 1
    ? viewRoute.matched[0]?.path || viewRoute.path
    : viewRoute.path
useModalEnter()

watch(
  usesAppShell,
  (isAppShellActive) => {
    if (isAppShellActive) {
      missionStore.startLifecycle()
    } else {
      missionStore.stopLifecycle()
      missionStore.reset()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  missionStore.stopLifecycle()
})
</script>

<template>
  <AppToast />
  <PointEarnedNotice />
  <div v-if="usesAppShell" class="app-shell d-flex min-vh-100">
    <SideNavigation />
    <div class="app-shell-body d-flex flex-grow-1 flex-column">
      <TopHeader />
      <main
        class="page-content flex-grow-1"
        :class="{ 'page-content--challenge-entry': route.name === 'current-challenge' }"
      >
        <div class="page-view">
          <RouterView v-slot="{ Component, route: viewRoute }">
            <Transition name="page" mode="out-in">
              <component :is="Component" :key="getRouteViewKey(viewRoute)" />
            </Transition>
          </RouterView>
        </div>
      </main>
    </div>
  </div>
  <RouterView v-else v-slot="{ Component, route: viewRoute }">
    <Transition name="page" mode="out-in">
      <component :is="Component" :key="getRouteViewKey(viewRoute)" />
    </Transition>
  </RouterView>
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

@media (min-width: 992px) {
  .page-content--challenge-entry {
    min-height: 0;
    padding-top: calc(var(--wallo-header-height) + 8px);
    padding-bottom: 0;
  }
}
</style>
