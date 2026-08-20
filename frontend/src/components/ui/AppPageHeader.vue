<script setup>
defineProps({
  title: {
    type: String,
    required: true,
  },
  titleId: {
    type: String,
    default: "",
  },
  eyebrow: {
    type: String,
    default: "",
  },
  description: {
    type: String,
    default: "",
  },
  titleTag: {
    type: String,
    default: "h1",
    validator: (value) => ["h1", "h2", "h3"].includes(value),
  },
  align: {
    type: String,
    default: "start",
    validator: (value) => ["start", "center"].includes(value),
  },
  compact: {
    type: Boolean,
    default: false,
  },
})
</script>

<template>
  <header
    class="app-page-header"
    :class="[`app-page-header--${align}`, { 'app-page-header--compact': compact }]"
  >
    <div v-if="$slots.leading" class="app-page-header__leading">
      <slot name="leading" />
    </div>
    <div class="app-page-header__main">
      <p v-if="eyebrow" class="app-page-header__eyebrow">{{ eyebrow }}</p>
      <component :is="titleTag" :id="titleId || undefined" class="app-page-header__title">
        <slot name="title">{{ title }}</slot>
      </component>
      <p v-if="description" class="app-page-header__description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="app-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.app-page-header {
  display: flex;
  align-items: flex-start;
  gap: var(--wallo-space-4);
  margin-top: var(--wallo-page-header-margin-top);
  margin-bottom: var(--wallo-page-header-margin-bottom);
}

.app-page-header--center {
  align-items: center;
}

.app-page-header__leading {
  flex: 0 0 auto;
}

.app-page-header__main {
  min-width: 0;
  flex: 1;
}

.app-page-header__eyebrow {
  margin: 0 0 var(--wallo-space-1);
  color: var(--wallo-color-primary);
  font-size: 0.875rem;
  font-weight: 800;
}

.app-page-header__title {
  margin: 0;
  color: var(--wallo-color-text);
  font-size: var(--wallo-page-title-size);
  font-weight: 800;
  line-height: 1.3;
}

.app-page-header__description {
  max-width: 680px;
  margin: var(--wallo-space-2) 0 0;
  color: var(--wallo-color-text-muted);
  line-height: 1.6;
}

.app-page-header__actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--wallo-space-2);
}

@media (max-width: 576px) {
  .app-page-header {
    flex-wrap: wrap;
  }

  .app-page-header__main {
    flex-basis: calc(100% - 48px);
  }

  .app-page-header__actions {
    flex-basis: 100%;
    justify-content: flex-start;
  }
}
</style>
