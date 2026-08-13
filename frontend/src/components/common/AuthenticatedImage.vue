<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue"
import httpClient from "@/api/httpClient"

const DEFAULT_PROFILE_IMAGE = "/images/profiles/default-profile.svg"

const props = defineProps({
  src: {
    type: String,
    default: "",
  },
  fallbackSrc: {
    type: String,
    default: DEFAULT_PROFILE_IMAGE,
  },
})

defineOptions({ inheritAttrs: false })

const objectUrl = ref("")
const hasLoadError = ref(false)
let requestSequence = 0

const isProtectedImageUrl = (source) =>
  typeof source === "string" && source.includes("/api/profile-images/")

const displaySrc = computed(() => {
  if (objectUrl.value) {
    return objectUrl.value
  }

  if (!props.src || hasLoadError.value) {
    return props.fallbackSrc
  }

  return isProtectedImageUrl(props.src) ? props.fallbackSrc : props.src
})

const revokeObjectUrl = () => {
  if (!objectUrl.value) {
    return
  }

  URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = ""
}

const loadProtectedImage = async (source) => {
  const currentRequest = ++requestSequence
  revokeObjectUrl()
  hasLoadError.value = false

  if (!isProtectedImageUrl(source)) {
    return
  }

  try {
    const response = await httpClient.get(source, { responseType: "blob" })
    const nextObjectUrl = URL.createObjectURL(response.data)

    if (currentRequest !== requestSequence) {
      URL.revokeObjectURL(nextObjectUrl)
      return
    }

    objectUrl.value = nextObjectUrl
  } catch (error) {
    if (currentRequest === requestSequence) {
      hasLoadError.value = true
    }
  }
}

const handleImageError = () => {
  if (displaySrc.value === props.fallbackSrc) {
    return
  }

  revokeObjectUrl()
  hasLoadError.value = true
}

watch(() => props.src, loadProtectedImage, { immediate: true })

onBeforeUnmount(() => {
  requestSequence += 1
  revokeObjectUrl()
})
</script>

<template>
  <img :src="displaySrc" v-bind="$attrs" @error="handleImageError" />
</template>
