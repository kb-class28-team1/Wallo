<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/userStore'

const userStore = useUserStore()
const { nickname, profileImageUrl, pointBalance, isLoading } = storeToRefs(userStore)

// 포인트 숫자에 천 단위 구분 기호를 적용함
const formattedPointBalance = computed(() => pointBalance.value.toLocaleString('ko-KR'))

onMounted(() => {
  userStore.fetchUserProfile()
})
</script>

<template>
  <header class="top-header d-flex flex-shrink-0 align-items-center justify-content-end">
    <div class="user-summary d-flex align-items-center">
      <img
        :src="profileImageUrl"
        class="profile-image rounded-circle"
        alt="사용자 프로필"
        @error="userStore.useDefaultProfileImage"
      />

      <span class="user-name">
        {{ isLoading && !nickname ? '불러오는 중...' : nickname || 'username' }}
      </span>

      <span class="point-badge d-inline-flex align-items-center">
        <span class="point-icon" aria-hidden="true">●</span>
        {{ formattedPointBalance }} P
      </span>

      <!-- 로그아웃 기능은 연결하지 않고 디자인만 표시함 -->
      <button type="button" class="logout-button" aria-label="로그아웃">
        <span aria-hidden="true">[→</span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.top-header {
  min-height: 68px;
  padding: 0 22px;
  background: #f1f2ff;
}

.user-summary {
  gap: 14px;
}

.profile-image {
  width: 44px;
  height: 44px;
  object-fit: cover;
  background: #ffffff;
}

.user-name {
  max-width: 220px;
  overflow: hidden;
  color: #111111;
  font-size: 22px;
  font-weight: 500;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.point-badge {
  gap: 5px;
  padding: 5px 12px;
  border: 1px solid #f0d99b;
  border-radius: 999px;
  color: #b97800;
  background: #fff8df;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.point-icon {
  color: #f4b52f;
  font-size: 9px;
}

.logout-button {
  padding: 0 0 0 6px;
  border: 0;
  color: #5d62c8;
  background: transparent;
  font-family: inherit;
  font-size: 29px;
  line-height: 1;
  cursor: default;
}

@media (max-width: 767.98px) {
  .top-header {
    padding: 0 14px;
  }

  .user-summary {
    gap: 9px;
  }

  .user-name {
    max-width: 110px;
    font-size: 18px;
  }
}
</style>
