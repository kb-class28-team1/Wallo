<script setup>
import { computed, onMounted } from "vue"
import { storeToRefs } from "pinia"
import { RouterLink, useRouter } from "vue-router"
import { useUserStore } from "@/stores/userStore"

const userStore = useUserStore()
const router = useRouter()
const { nickname, profileImageUrl, pointBalance, isLoading } = storeToRefs(userStore)

// 포인트 숫자에 천 단위 구분 기호를 적용함
const formattedPointBalance = computed(() => pointBalance.value.toLocaleString("ko-KR"))
const displayedNickname = computed(() =>
  isLoading.value && !nickname.value ? "불러오는 중..." : nickname.value || "username",
)

onMounted(() => {
  userStore.fetchUserProfile()
})

const handleLogout = async () => {
  try {
    await userStore.logout()
    await router.replace("/login")
  } catch (error) {
    alert(error.message || "로그아웃에 실패했습니다.")
  }
}
</script>

<template>
  <header class="top-header d-flex flex-shrink-0 align-items-center justify-content-end">
    <div class="user-summary d-flex align-items-center">
      <!-- 프로필 이미지와 이름을 누르면 설정 페이지로 이동함 -->
      <RouterLink
        to="/users/profile"
        class="profile-link d-flex align-items-center"
        aria-label="설정 페이지로 이동"
      >
        <img
          :src="profileImageUrl"
          class="profile-image rounded-circle"
          alt="사용자 프로필"
          @error="userStore.useDefaultProfileImage"
        />

        <span class="user-name">
          {{ displayedNickname }}
        </span>
      </RouterLink>

      <!-- 보유 포인트를 누르면 포인트 샵으로 이동함 -->
      <RouterLink
        to="/point-shop"
        class="point-badge d-inline-flex align-items-center"
        aria-label="포인트 샵으로 이동"
      >
        <span class="point-icon" aria-hidden="true">●</span>
        {{ formattedPointBalance }} P
      </RouterLink>

      <button
        type="button"
        class="logout-button"
        aria-label="로그아웃"
        :disabled="isLoading"
        @click="handleLogout"
      >
        <span aria-hidden="true">[→</span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.top-header {
  position: fixed;
  z-index: 1020;
  top: 0;
  right: 0;
  left: 273px;
  height: 68px;
  min-height: 68px;
  padding: 0 22px;
  background: #f1f2ff;
}

.user-summary {
  gap: 14px;
}

.profile-link {
  gap: 14px;
  color: inherit;
  text-decoration: none;
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
  text-decoration: none;
}

.point-icon {
  color: #f4b52f;
  font-size: 9px;
}

.logout-button {
  display: inline-flex;
  padding: 0 0 0 6px;
  border: 0;
  color: #5d62c8;
  background: transparent;
  font-family: inherit;
  font-size: 29px;
  line-height: 1;
  cursor: pointer;
  text-decoration: none;
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
