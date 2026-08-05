<script setup>
import { computed, onBeforeUnmount, onMounted } from "vue"
import { RouterView, useRoute } from "vue-router"
import SideNavigation from "@/components/navigation/SideNavigation.vue"
import TopHeader from "@/components/navigation/TopHeader.vue"

const route = useRoute()
const usesAppShell = computed(() => Boolean(route.meta.appShell))

// 커스텀 모달이 열려 있을 때 포커스가 모달 밖에 있어도 Enter로 기본 동작을 실행함.
// 입력창·텍스트 영역에서는 각 컴포넌트의 폼 동작을 우선하므로 가로채지 않음.
const handleModalEnter = (event) => {
  if (event.key !== "Enter" || event.isComposing) return

  const dialog = document.querySelector('[role="dialog"]')
  if (!dialog) return

  const target = event.target
  const targetInsideDialog = target instanceof Element && dialog.contains(target)
  if (
    targetInsideDialog &&
    target.matches("input, textarea, select, button, [contenteditable=\"true\"]")
  ) {
    return
  }

  const actionButton = dialog.querySelector(
    "[data-modal-confirm], .modal-footer .btn-primary, .modal-footer .submit, .submit",
  ) || dialog.querySelector("[aria-label=\"닫기\"], .btn-close")
  if (!actionButton || actionButton.disabled) return

  event.preventDefault()
  actionButton.click()
}

onMounted(() => window.addEventListener("keyup", handleModalEnter))
onBeforeUnmount(() => window.removeEventListener("keyup", handleModalEnter))
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
