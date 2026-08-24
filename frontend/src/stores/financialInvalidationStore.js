import { ref } from "vue"
import { defineStore } from "pinia"

export const useFinancialInvalidationStore = defineStore("financialInvalidation", () => {
  const revision = ref(0)
  const lastChangedAt = ref(0)

  const markChanged = () => {
    revision.value += 1
    lastChangedAt.value = Date.now()
    return revision.value
  }

  return {
    revision,
    lastChangedAt,
    markChanged,
  }
})
