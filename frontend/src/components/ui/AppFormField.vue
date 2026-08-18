<script setup>
import { computed } from "vue"

defineOptions({ inheritAttrs: false })

let fieldInstance = 0

const props = defineProps({
  id: {
    type: String,
    default: "",
  },
  name: {
    type: String,
    default: "",
  },
  label: {
    type: String,
    default: "",
  },
  modelValue: {
    type: [String, Number, Boolean],
    default: "",
  },
  type: {
    type: String,
    default: "text",
  },
  placeholder: {
    type: String,
    default: "",
  },
  helpText: {
    type: String,
    default: "",
  },
  error: {
    type: String,
    default: "",
  },
  required: {
    type: Boolean,
    default: false,
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  readonly: {
    type: Boolean,
    default: false,
  },
  multiline: {
    type: Boolean,
    default: false,
  },
  rows: {
    type: [Number, String],
    default: 4,
  },
})

const emit = defineEmits(["update:modelValue", "input", "change", "blur"])
const generatedId = `app-form-field-${++fieldInstance}`

const fieldId = computed(() => props.id || generatedId)
const assistiveText = computed(() => props.error || props.helpText)
const assistiveTextId = computed(() => `${fieldId.value}-message`)

function handleInput(event) {
  emit("update:modelValue", event.target.value)
  emit("input", event)
}

function handleChange(event) {
  emit("change", event)
}

function handleBlur(event) {
  emit("blur", event)
}
</script>

<template>
  <div class="app-form-field" :class="{ 'app-form-field--error': error }">
    <label v-if="label" class="app-form-field__label" :for="fieldId">
      <span>{{ label }}</span>
      <span v-if="required" class="app-form-field__required" aria-hidden="true">*</span>
    </label>
    <div class="app-form-field__control">
      <slot
        :id="fieldId"
        :described-by="assistiveText ? assistiveTextId : undefined"
        :invalid="Boolean(error)"
      >
        <textarea
          v-if="multiline"
          v-bind="$attrs"
          :id="fieldId"
          :name="name || undefined"
          :value="modelValue ?? ''"
          :placeholder="placeholder || undefined"
          :rows="rows"
          :required="required"
          :disabled="disabled"
          :readonly="readonly"
          :aria-describedby="assistiveText ? assistiveTextId : undefined"
          :aria-invalid="error ? 'true' : undefined"
          class="app-form-field__input app-form-field__textarea"
          @input="handleInput"
          @change="handleChange"
          @blur="handleBlur"
        ></textarea>
        <input
          v-else
          v-bind="$attrs"
          :id="fieldId"
          :name="name || undefined"
          :type="type"
          :value="modelValue ?? ''"
          :placeholder="placeholder || undefined"
          :required="required"
          :disabled="disabled"
          :readonly="readonly"
          :aria-describedby="assistiveText ? assistiveTextId : undefined"
          :aria-invalid="error ? 'true' : undefined"
          class="app-form-field__input"
          @input="handleInput"
          @change="handleChange"
          @blur="handleBlur"
        />
      </slot>
    </div>
    <p v-if="assistiveText" :id="assistiveTextId" class="app-form-field__message">
      {{ assistiveText }}
    </p>
  </div>
</template>

<style scoped>
.app-form-field {
  display: flex;
  flex-direction: column;
  gap: var(--wallo-space-2);
  min-width: 0;
}

.app-form-field__label {
  display: inline-flex;
  align-items: baseline;
  gap: var(--wallo-space-1);
  color: var(--wallo-color-text);
  font-weight: 700;
}

.app-form-field__required {
  color: var(--wallo-color-danger);
}

.app-form-field__control {
  min-width: 0;
}

.app-form-field__input {
  display: block;
  width: 100%;
  min-height: 44px;
  padding: 0 var(--wallo-space-3);
  color: var(--wallo-color-text);
  font: inherit;
  background: var(--wallo-color-surface);
  border: 1px solid var(--wallo-color-border);
  border-radius: var(--wallo-radius-md);
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.app-form-field__textarea {
  min-height: 112px;
  padding-top: var(--wallo-space-3);
  padding-bottom: var(--wallo-space-3);
  resize: vertical;
}

.app-form-field__input::placeholder {
  color: var(--wallo-color-text-subtle);
}

.app-form-field__input:focus {
  outline: 0;
  border-color: var(--wallo-color-primary);
  box-shadow: var(--wallo-focus-ring);
}

.app-form-field__input:disabled {
  color: var(--wallo-color-text-subtle);
  background: var(--wallo-color-surface-soft);
  cursor: not-allowed;
}

.app-form-field__input:read-only {
  background: var(--wallo-color-surface-soft);
}

.app-form-field--error .app-form-field__input {
  border-color: var(--wallo-color-danger);
}

.app-form-field--error .app-form-field__input:focus {
  box-shadow: 0 0 0 3px rgb(220 53 69 / 16%);
}

.app-form-field__message {
  margin: 0;
  color: var(--wallo-color-text-muted);
  font-size: 0.875rem;
  line-height: 1.5;
}

.app-form-field--error .app-form-field__message {
  color: var(--wallo-color-danger);
}
</style>
