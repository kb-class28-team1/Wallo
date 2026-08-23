<script setup>
import { computed, nextTick, ref, watch } from "vue"

let tabsInstance = 0

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
  modelValue: {
    type: [String, Number],
    default: null,
  },
  variant: {
    type: String,
    default: "line",
    validator: (value) => ["line", "pill", "segment"].includes(value),
  },
  ariaLabel: {
    type: String,
    default: "탭 메뉴",
  },
  fullWidth: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(["update:modelValue", "change"])
const internalValue = ref(null)
const tabButtons = ref([])
const instanceId = `app-tabs-${++tabsInstance}`

const normalizedItems = computed(() =>
  props.items.map((item, index) => {
    if (typeof item === "string" || typeof item === "number") {
      return { value: item, label: String(item), disabled: false }
    }

    return {
      ...item,
      value: item.value ?? index,
      label: item.label ?? String(item.value ?? index),
      disabled: Boolean(item.disabled),
    }
  }),
)

const firstEnabledItem = computed(
  () => normalizedItems.value.find((item) => !item.disabled) || null,
)

const selectedValue = computed(() => {
  const requestedValue = props.modelValue ?? internalValue.value
  const requestedItem = normalizedItems.value.find(
    (item) => item.value === requestedValue && !item.disabled,
  )

  return requestedItem?.value ?? firstEnabledItem.value?.value ?? null
})

watch(
  normalizedItems,
  (items) => {
    if (!items.some((item) => item.value === internalValue.value && !item.disabled)) {
      internalValue.value = items.find((item) => !item.disabled)?.value ?? null
    }
  },
  { immediate: true, deep: true },
)

function tabId(index) {
  return `${instanceId}-tab-${index}`
}

function setTabButton(element, index) {
  tabButtons.value[index] = element
}

function selectTab(item) {
  if (item.disabled) return
  internalValue.value = item.value
  emit("update:modelValue", item.value)
  emit("change", item.value)
}

function focusTab(index) {
  const items = normalizedItems.value
  if (!items.length) return

  let targetIndex = index
  for (let attempt = 0; attempt < items.length; attempt += 1) {
    const target = items[targetIndex]
    if (target && !target.disabled) {
      selectTab(target)
      nextTick(() => tabButtons.value[targetIndex]?.focus())
      return
    }
    targetIndex = (targetIndex + 1) % items.length
  }
}

function handleKeydown(event, currentIndex) {
  const lastIndex = normalizedItems.value.length - 1
  let targetIndex = null

  if (event.key === "ArrowRight" || event.key === "ArrowDown") {
    targetIndex = (currentIndex + 1) % normalizedItems.value.length
  } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
    targetIndex = (currentIndex - 1 + normalizedItems.value.length) % normalizedItems.value.length
  } else if (event.key === "Home") {
    targetIndex = 0
  } else if (event.key === "End") {
    targetIndex = lastIndex
  }

  if (targetIndex === null || !normalizedItems.value.length) return
  event.preventDefault()
  focusTab(targetIndex)
}
</script>

<template>
  <div class="app-tabs" :class="[`app-tabs--${variant}`, { 'app-tabs--full-width': fullWidth }]">
    <div class="app-tabs__list" role="tablist" :aria-label="ariaLabel">
      <button
        v-for="(item, index) in normalizedItems"
        :key="item.value"
        :ref="(element) => setTabButton(element, index)"
        type="button"
        class="app-tabs__tab pressable"
        :class="{ 'app-tabs__tab--active': item.value === selectedValue }"
        :id="tabId(index)"
        role="tab"
        :aria-selected="item.value === selectedValue"
        :tabindex="item.disabled ? -1 : item.value === selectedValue ? 0 : -1"
        :disabled="item.disabled"
        @click="selectTab(item)"
        @keydown="handleKeydown($event, index)"
      >
        <slot name="tab" :item="item" :active="item.value === selectedValue" :index="index">
          {{ item.label }}
        </slot>
      </button>
    </div>
    <div v-if="$slots.default" class="app-tabs__content">
      <slot :active-value="selectedValue" />
    </div>
  </div>
</template>

<style scoped>
.app-tabs {
  min-width: 0;
}

.app-tabs__list {
  display: flex;
  gap: var(--wallo-space-2);
  overflow-x: auto;
  scrollbar-width: thin;
}

.app-tabs__tab {
  flex: 0 0 auto;
  min-height: 42px;
  padding: 0 var(--wallo-space-4);
  color: var(--wallo-color-text-muted);
  font: inherit;
  font-weight: 700;
  white-space: nowrap;
  background: transparent;
  border: 0;
  cursor: pointer;
  transition:
    background-color 160ms ease,
    color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

.app-tabs__tab:focus-visible {
  outline: 0;
  box-shadow: var(--wallo-focus-ring);
}

.app-tabs__tab:disabled {
  color: var(--wallo-color-text-subtle);
  cursor: not-allowed;
  opacity: 0.62;
}

.app-tabs--line .app-tabs__list {
  border-bottom: 1px solid var(--wallo-color-border);
}

.app-tabs--line .app-tabs__tab {
  margin-bottom: -1px;
  border-bottom: 2px solid transparent;
}

.app-tabs--line .app-tabs__tab:hover:not(:disabled),
.app-tabs--line .app-tabs__tab--active {
  color: var(--wallo-color-primary);
  border-bottom-color: var(--wallo-color-primary);
}

.app-tabs--pill .app-tabs__list {
  width: fit-content;
  max-width: 100%;
  padding: var(--wallo-space-1);
  background: var(--wallo-color-surface-soft);
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: var(--wallo-radius-pill);
}

.app-tabs--pill .app-tabs__tab {
  min-height: 36px;
  border-radius: var(--wallo-radius-pill);
}

.app-tabs--pill .app-tabs__tab:hover:not(:disabled) {
  color: var(--wallo-color-primary);
}

.app-tabs--pill .app-tabs__tab--active {
  color: var(--wallo-color-primary);
  background: var(--wallo-color-surface);
  box-shadow: var(--wallo-shadow-card);
}

.app-tabs--segment .app-tabs__list {
  padding: var(--wallo-space-1);
  background: var(--wallo-color-surface-soft);
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: var(--wallo-radius-md);
}

.app-tabs--segment .app-tabs__tab {
  flex: 1 1 0;
  min-width: 100px;
  border-radius: var(--wallo-radius-sm);
}

.app-tabs--segment .app-tabs__tab:hover:not(:disabled) {
  color: var(--wallo-color-primary);
}

.app-tabs--segment .app-tabs__tab--active {
  color: var(--wallo-color-primary);
  background: var(--wallo-color-surface);
  box-shadow: var(--wallo-shadow-card);
}

.app-tabs--full-width .app-tabs__list {
  width: 100%;
}

.app-tabs--full-width .app-tabs__tab {
  flex: 1 1 0;
}

.app-tabs__content {
  margin-top: var(--wallo-space-4);
}
</style>
