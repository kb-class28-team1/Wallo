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
        {{ message.role === "assistant" ? "Wallo AI" : "나" }}
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
  color: #7b849b;
  font-size: 12px;
}

.message-row--user .message-label {
  text-align: right;
}

.message-bubble p {
  margin: 0;
  padding: 12px 14px;
  background: #f4f3fb;
  border-radius: 14px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.message-row--user .message-bubble p {
  color: #fff;
  background: #7062de;
}

@media (max-width: 640px) {
  .message-bubble {
    max-width: 90%;
  }
}
</style>
