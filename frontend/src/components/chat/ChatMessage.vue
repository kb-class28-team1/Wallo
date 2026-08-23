<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue"
import DOMPurify from "dompurify"
import { marked } from "marked"
import AssetAnalysisResult from "@/components/analysis/AssetAnalysisResult.vue"
import AnalysisResult from "@/components/analysis/AnalysisResult.vue"
import ProductRecommendationResult from "@/components/analysis/ProductRecommendationResult.vue"

const props = defineProps({
  message: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(["typing", "typing-complete"])
const displayedContent = ref("")
const isTyping = ref(false)
let typingTimer = null
const TYPING_INTERVAL_MS = 35

marked.setOptions({
  breaks: true,
  gfm: true,
})

const renderedMarkdown = computed(() =>
  DOMPurify.sanitize(marked.parse(displayedContent.value)),
)

const isAnalysisMessage = computed(() =>
  props.message.role === "assistant"
  && Boolean(
    props.message.consumptionAnalysis
      || props.message.assetAnalysis
      || props.message.productRecommendation,
  ),
)

const stopTyping = () => {
  if (typingTimer) {
    clearInterval(typingTimer)
    typingTimer = null
  }
  isTyping.value = false
}

const completeTyping = () => {
  const wasTyping = isTyping.value
  stopTyping()
  if (wasTyping) {
    emit("typing-complete", props.message.id)
  }
}

const startTyping = () => {
  stopTyping()

  const content = props.message.content || ""
  if (isAnalysisMessage.value) {
    displayedContent.value = ""
    if (props.message.animate) {
      emit("typing-complete", props.message.id)
    }
    return
  }
  if (props.message.role !== "assistant" || !props.message.animate) {
    displayedContent.value = content
    return
  }

  const characters = Array.from(content)
  const charactersPerTick = characters.length > 1500 ? 4 : characters.length > 600 ? 2 : 1
  let cursor = 0
  displayedContent.value = ""
  isTyping.value = true

  typingTimer = setInterval(() => {
    cursor = Math.min(cursor + charactersPerTick, characters.length)
    displayedContent.value = characters.slice(0, cursor).join("")
    emit("typing")

    if (cursor >= characters.length) {
      completeTyping()
    }
  }, TYPING_INTERVAL_MS)
}

watch(
  () => [
    props.message.content,
    props.message.animate,
    props.message.consumptionAnalysis,
    props.message.assetAnalysis,
    props.message.productRecommendation,
  ],
  startTyping,
  { immediate: true },
)

onBeforeUnmount(completeTyping)
</script>

<template>
  <div class="message-row" :class="`message-row--${message.role}`">
    <div
      class="message-bubble"
      :class="{ 'message-bubble--analysis': isAnalysisMessage }"
    >
      <ProductRecommendationResult
        v-if="message.role === 'assistant' && message.productRecommendation"
        :recommendation="message.productRecommendation"
        :reason="message.content"
      />
      <AssetAnalysisResult
        v-else-if="message.role === 'assistant' && message.assetAnalysis"
        :analysis="message.assetAnalysis"
        :show-intro="false"
      />
      <AnalysisResult
        v-else-if="message.role === 'assistant' && message.consumptionAnalysis"
        :analysis="message.consumptionAnalysis"
      />
      <div
        v-else-if="message.role === 'assistant'"
        class="message-content message-content--markdown"
        :class="{ 'message-content--typing': isTyping }"
        v-html="renderedMarkdown"
      ></div>
      <p v-else class="message-content">{{ displayedContent }}</p>
    </div>
  </div>
</template>

<style scoped>
.message-row {
  display: flex;
  margin-bottom: 16px;
}

.message-row--user {
  justify-content: flex-end;
}

.message-bubble {
  max-width: 75%;
}

.message-bubble--analysis {
  width: min(100%, 720px);
  max-width: 92%;
}

.message-content {
  margin: 0;
  padding: 12px 14px;
  background: var(--wallo-color-surface-soft);
  border-radius: 14px;
  line-height: 1.35;
  white-space: pre-wrap;
}

.message-row--user .message-content {
  color: #fff;
  background: #4f8fe8;
}

.message-content--markdown {
  line-height: 1.55;
  white-space: normal;
}

.message-content--markdown :deep(> :first-child) {
  margin-top: 0;
}

.message-content--markdown :deep(> :last-child) {
  margin-bottom: 0;
}

.message-content--typing:empty::after,
.message-content--typing :deep(> p:last-child)::after,
.message-content--typing :deep(> h1:last-child)::after,
.message-content--typing :deep(> h2:last-child)::after,
.message-content--typing :deep(> h3:last-child)::after,
.message-content--typing :deep(> h4:last-child)::after,
.message-content--typing :deep(> h5:last-child)::after,
.message-content--typing :deep(> h6:last-child)::after,
.message-content--typing :deep(> ul:last-child > li:last-child)::after,
.message-content--typing :deep(> ol:last-child > li:last-child)::after,
.message-content--typing :deep(> blockquote:last-child > :last-child)::after {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 4px;
  vertical-align: text-bottom;
  background: #4f8fe8;
  content: "";
  animation: cursor-blink 0.8s step-end infinite;
}

.message-content--markdown :deep(h1),
.message-content--markdown :deep(h2),
.message-content--markdown :deep(h3) {
  margin: 0.8em 0 0.45em;
  font-size: 1.05rem;
  font-weight: 700;
}

.message-content--markdown :deep(p),
.message-content--markdown :deep(ul),
.message-content--markdown :deep(ol),
.message-content--markdown :deep(blockquote),
.message-content--markdown :deep(pre) {
  margin: 0 0 0.7em;
}

.message-content--markdown :deep(p) {
  margin-bottom: 0.55em;
}

.message-content--markdown :deep(p:empty) {
  display: none;
}

.message-content--markdown :deep(ul),
.message-content--markdown :deep(ol) {
  padding-left: 1.4rem;
}

.message-content--markdown :deep(li + li) {
  margin-top: 0.25em;
}

.message-content--markdown :deep(code) {
  padding: 0.15em 0.35em;
  background: var(--wallo-color-info-bg);
  border-radius: 5px;
  font-size: 0.9em;
}

.message-content--markdown :deep(pre) {
  overflow-x: auto;
  padding: 12px;
  color: #f5f5f5;
  background: #29273a;
  border-radius: 10px;
}

.message-content--markdown :deep(pre code) {
  padding: 0;
  color: inherit;
  background: transparent;
}

.message-content--markdown :deep(blockquote) {
  padding-left: 12px;
  color: #666d80;
  border-left: 3px solid #8fb3e8;
}

.message-content--markdown :deep(a) {
  color: #3f78cd;
}

@keyframes cursor-blink {
  50% {
    opacity: 0;
  }
}

@media (max-width: 640px) {
  .message-bubble {
    max-width: 90%;
  }
}
</style>
