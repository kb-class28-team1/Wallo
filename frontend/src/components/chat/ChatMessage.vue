<script setup>
defineProps({
  message: {
    type: Object,
    required: true,
  },
})
</script>

<template>
  <div class="message-row" :class="`message-row--${message.role}`">
    <div class="message-bubble">
      <span class="message-label">
        {{ message.role === 'assistant' ? 'AI' : '사용자' }}
      </span>
      <p>{{ message.content }}</p>
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

.message-label {
  display: block;
  margin-bottom: 4px;
  color: #666;
  font-size: 12px;
}

.message-row--user .message-label {
  text-align: right;
}

.message-bubble p {
  margin: 0;
  padding: 12px;
  background: #f1f1f1;
  border: 1px solid #ddd;
  border-radius: 8px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.message-row--user .message-bubble p {
  background: #e5e5e5;
}

@media (max-width: 640px) {
  .message-bubble {
    max-width: 90%;
  }
}
</style>
