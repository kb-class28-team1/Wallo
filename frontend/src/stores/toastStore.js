import { ref } from "vue"
import { defineStore } from "pinia"

const DEFAULT_DURATION_MS = 6000

export const useToastStore = defineStore("toast", () => {
  const toasts = ref([])
  let nextToastId = 0

  const remove = (toastId) => {
    toasts.value = toasts.value.filter((toast) => toast.id !== toastId)
  }

  const show = (message, { variant = "danger", duration = DEFAULT_DURATION_MS } = {}) => {
    if (!message) return null

    const toastId = ++nextToastId
    toasts.value.push({
      id: toastId,
      message,
      variant,
    })

    if (duration > 0) {
      setTimeout(() => remove(toastId), duration)
    }

    return toastId
  }

  const clear = () => {
    toasts.value = []
  }

  return {
    toasts,
    show,
    remove,
    clear,
  }
})
