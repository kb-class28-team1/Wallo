<script setup>
import { RouterLink, RouterView, useRoute } from "vue-router"
import AppCard from "@/components/ui/AppCard.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"

const route = useRoute()

const tabs = [
  { name: "user-profile", label: "프로필 편집" },
  { name: "connection-management", label: "연결된 자산" },
  { name: "password-settings", label: "비밀번호 변경" },
]

const isActiveTab = (tabName) => route.name === tabName
</script>

<template>
  <section class="settings-view">
    <AppPageHeader title="설정" />

    <AppCard class="settings-navigation" variant="soft" padding="none">
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
    </AppCard>

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
  padding: var(--wallo-space-6) var(--wallo-space-4);
}

.settings-navigation {
  margin-bottom: var(--wallo-space-5);
  border-radius: var(--wallo-radius-lg);
}

.settings-tabs {
  display: flex;
  gap: var(--wallo-space-2);
  padding: var(--wallo-space-2) var(--wallo-space-3) 0;
}

.settings-tab {
  position: relative;
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  padding: 0 var(--wallo-space-3);
  color: var(--wallo-color-text-muted);
  font-size: 0.9rem;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
  border-radius: var(--wallo-radius-sm) var(--wallo-radius-sm) 0 0;
}

.settings-tab::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--wallo-color-primary);
  opacity: 0;
  content: "";
}

.settings-tab:hover,
.settings-tab:focus-visible,
.settings-tab-active {
  color: var(--wallo-color-primary);
}

.settings-tab-active::after {
  opacity: 1;
}

.settings-content {
  padding-top: var(--wallo-space-1);
}

@media (max-width: 575.98px) {
  .settings-view {
    padding-right: var(--wallo-space-3);
    padding-left: var(--wallo-space-3);
  }

  .settings-tabs {
    padding-right: var(--wallo-space-2);
    padding-left: var(--wallo-space-2);
  }

  .settings-tabs {
    gap: var(--wallo-space-1);
    overflow-x: auto;
  }

  .settings-tab {
    padding-right: var(--wallo-space-2);
    padding-left: var(--wallo-space-2);
    font-size: 0.85rem;
  }
}
</style>
