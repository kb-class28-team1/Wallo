<script setup>
import { computed } from "vue"
import { RouterView } from "vue-router"
import { useRoute } from "vue-router"
import SideNavigation from "@/components/navigation/SideNavigation.vue"
import TopHeader from "@/components/navigation/TopHeader.vue"

const route = useRoute()

// 자산 연결 화면은 화면 자체의 너비와 여백 스타일을 그대로 사용함
const pageContentClass = computed(() => ({
  "connection-page-content": route.path === "/connections/mydata",
}))
</script>

<template>
  <div class="default-layout d-flex min-vh-100">
    <SideNavigation />
    <div class="layout-body d-flex flex-grow-1 flex-column">
      <TopHeader />
      <main class="page-content flex-grow-1" :class="pageContentClass">
        <div class="page-view">
          <RouterView />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.default-layout {
  min-width: 100%;
  background: #fafafa;
}

.layout-body {
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

.connection-page-content {
  display: block;
  padding: 68px 0 0;
}

.connection-page-content .page-view {
  max-width: none;
}

@media (max-width: 767.98px) {
  .page-content {
    padding-right: 20px;
    padding-left: 20px;
  }

  .connection-page-content {
    padding-right: 0;
    padding-left: 0;
  }
}
</style>
