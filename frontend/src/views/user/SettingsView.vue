<script setup>
import { RouterLink, RouterView, useRoute } from "vue-router"

const route = useRoute()

const tabs = [
  { name: "user-profile", label: "프로필 편집" },
  { name: "connection-management", label: "연결된 자산" },
  { name: "password-settings", label: "비밀번호 변경" },
]

const isActiveTab = (tabName) => route.name === tabName
</script>

<template>
  <section class="settings-view container-fluid px-4 py-4">
    <header class="settings-header">
      <h1 class="h3 fw-bold mb-0">설정</h1>
    </header>

    <nav class="settings-tabs" aria-label="설정 메뉴">
      <RouterLink
        v-for="tab in tabs"
        :key="tab.name"
        :to="{ name: tab.name }"
        class="settings-tab"
        :class="{ 'settings-tab-active': isActiveTab(tab.name) }"
        :aria-current="isActiveTab(tab.name) ? 'page' : undefined"
      >
        {{ tab.label }}
      </RouterLink>
    </nav>

    <main class="settings-content">
      <RouterView v-slot="{ Component }">
        <KeepAlive>
          <component :is="Component" :key="route.name" />
        </KeepAlive>
      </RouterView>
    </main>
  </section>
</template>

<style scoped>
.settings-view {
  width: 100%;
  max-width: 980px;
  margin: 0 auto;
}

.settings-header {
  padding: 0 8px 24px;
}

.settings-tabs {
  display: flex;
  gap: 28px;
  padding: 0 8px;
  border-bottom: 1px solid #e6eaf2;
}

.settings-tab {
  position: relative;
  display: inline-flex;
  min-height: 48px;
  align-items: center;
  padding: 0 2px;
  color: #98a2b6;
  font-size: 0.95rem;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.settings-tab::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: transparent;
  content: "";
}

.settings-tab:hover,
.settings-tab:focus-visible,
.settings-tab-active {
  color: #6257e8;
}

.settings-tab-active::after {
  background: #6257e8;
}

.settings-content {
  padding-top: 24px;
}

@media (max-width: 575.98px) {
  .settings-view {
    padding-right: 0 !important;
    padding-left: 0 !important;
  }

  .settings-header,
  .settings-tabs {
    padding-right: 4px;
    padding-left: 4px;
  }

  .settings-tabs {
    gap: 20px;
    overflow-x: auto;
  }

  .settings-tab {
    font-size: 0.88rem;
  }
}
</style>
