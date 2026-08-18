<script setup>
import { computed } from "vue"

const props = defineProps({
  as: {
    type: [String, Object],
    default: "article",
  },
  variant: {
    type: String,
    default: "surface",
    validator: (value) => ["surface", "soft", "accent", "outline"].includes(value),
  },
  padding: {
    type: String,
    default: "md",
    validator: (value) => ["none", "sm", "md", "lg"].includes(value),
  },
  interactive: {
    type: Boolean,
    default: false,
  },
})

const cardClasses = computed(() => [
  "app-card",
  `app-card--${props.variant}`,
  `app-card--padding-${props.padding}`,
  {
    "app-card--interactive": props.interactive,
  },
])
</script>

<template>
  <component :is="as" :class="cardClasses">
    <header v-if="$slots.header" class="app-card__header">
      <slot name="header" />
    </header>
    <div v-if="$slots.default" class="app-card__body">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="app-card__footer">
      <slot name="footer" />
    </footer>
  </component>
</template>

<style scoped>
.app-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--wallo-color-border);
  border-radius: var(--wallo-radius-lg);
  color: var(--wallo-color-text);
}

.app-card--surface {
  background: var(--wallo-color-surface);
  box-shadow: var(--wallo-shadow-card);
}

.app-card--soft {
  background: var(--wallo-color-surface-soft);
}

.app-card--accent {
  background: var(--wallo-color-info-bg);
  border-color: color-mix(in srgb, var(--wallo-color-finance-info) 18%, var(--wallo-color-surface));
}

.app-card--outline {
  background: transparent;
  box-shadow: none;
}

.app-card--interactive {
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

.app-card--interactive:hover {
  border-color: color-mix(in srgb, var(--wallo-color-primary) 35%, var(--wallo-color-border));
  box-shadow:
    var(--wallo-shadow-card),
    0 10px 28px rgb(48 60 110 / 7%);
  transform: translateY(-1px);
}

.app-card--padding-none {
  padding: 0;
}

.app-card--padding-sm {
  padding: var(--wallo-space-3);
}

.app-card--padding-md {
  padding: var(--wallo-space-5);
}

.app-card--padding-lg {
  padding: var(--wallo-space-6);
}

.app-card__header,
.app-card__footer {
  display: flex;
  align-items: center;
  gap: var(--wallo-space-3);
}

.app-card__header {
  justify-content: space-between;
  margin-bottom: var(--wallo-space-4);
}

.app-card__body {
  min-width: 0;
}

.app-card__footer {
  justify-content: flex-end;
  margin-top: var(--wallo-space-4);
}
</style>
