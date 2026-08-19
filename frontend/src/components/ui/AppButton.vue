<script setup>
import { computed } from "vue"

const props = defineProps({
  variant: {
    type: String,
    default: "primary",
    validator: (value) => ["primary", "secondary", "outline", "danger", "ghost"].includes(value),
  },
  size: {
    type: String,
    default: "md",
    validator: (value) => ["sm", "md", "lg"].includes(value),
  },
  type: {
    type: String,
    default: "button",
    validator: (value) => ["button", "submit", "reset"].includes(value),
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  block: {
    type: Boolean,
    default: false,
  },
  pill: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(["click"])

const isDisabled = computed(() => props.disabled || props.loading)
const buttonClasses = computed(() => [
  "app-button",
  `app-button--${props.variant}`,
  `app-button--${props.size}`,
  {
    "app-button--block": props.block,
    "app-button--pill": props.pill,
  },
])

function handleClick(event) {
  if (isDisabled.value) return
  emit("click", event)
}
</script>

<template>
  <button
    :type="type"
    :class="buttonClasses"
    :disabled="isDisabled"
    :aria-busy="loading || undefined"
    @click="handleClick"
  >
    <span v-if="loading" class="app-button__spinner" aria-hidden="true"></span>
    <span v-else-if="$slots.leading" class="app-button__icon" aria-hidden="true">
      <slot name="leading" />
    </span>
    <span class="app-button__label"><slot /></span>
    <span v-if="$slots.trailing" class="app-button__icon" aria-hidden="true">
      <slot name="trailing" />
    </span>
  </button>
</template>

<style scoped>
.app-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--wallo-space-2);
  min-width: 0;
  border: 1px solid transparent;
  border-radius: var(--wallo-radius-md);
  font: inherit;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 160ms ease,
    border-color 160ms ease,
    box-shadow 160ms ease,
    color 160ms ease,
    opacity 160ms ease;
}

.app-button:focus-visible {
  outline: 0;
  box-shadow: var(--wallo-focus-ring);
}

.app-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.app-button--sm {
  min-height: 36px;
  padding: 0 var(--wallo-space-3);
  font-size: 0.875rem;
  border-radius: var(--wallo-radius-sm);
}

.app-button--md {
  min-height: 44px;
  padding: 0 var(--wallo-space-4);
}

.app-button--lg {
  min-height: 52px;
  padding: 0 var(--wallo-space-5);
  font-size: 1.05rem;
}

.app-button--block {
  width: 100%;
}

.app-button--pill {
  border-radius: var(--wallo-radius-pill);
}

.app-button--primary {
  color: var(--wallo-color-surface);
  background: var(--wallo-color-primary);
  border-color: var(--wallo-color-primary);
}

.app-button--primary:hover:not(:disabled) {
  background: var(--wallo-color-primary-hover);
  border-color: var(--wallo-color-primary-hover);
}

.app-button--secondary {
  color: var(--wallo-color-text);
  background: var(--wallo-color-surface-soft);
  border-color: var(--wallo-color-border);
}

.app-button--secondary:hover:not(:disabled) {
  background: var(--wallo-color-surface);
  border-color: var(--wallo-color-text-muted);
}

.app-button--outline {
  color: var(--wallo-color-primary);
  background: transparent;
  border-color: var(--wallo-color-primary);
}

.app-button--outline:hover:not(:disabled) {
  color: var(--wallo-color-primary-hover);
  background: rgb(112 98 222 / 8%);
  border-color: var(--wallo-color-primary-hover);
}

.app-button--danger {
  color: var(--wallo-color-surface);
  background: var(--wallo-color-danger);
  border-color: var(--wallo-color-danger);
}

.app-button--danger:hover:not(:disabled) {
  background: #bb2d3b;
  border-color: #bb2d3b;
}

.app-button--ghost {
  color: var(--wallo-color-text-muted);
  background: transparent;
  border-color: transparent;
}

.app-button--ghost:hover:not(:disabled) {
  color: var(--wallo-color-primary);
  background: rgb(112 98 222 / 8%);
}

.app-button__label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-button__icon {
  display: inline-flex;
  align-items: center;
  font-size: 1.1em;
}

.app-button__spinner {
  width: 1em;
  height: 1em;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: app-button-spin 700ms linear infinite;
}

@keyframes app-button-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
