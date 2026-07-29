import { ref } from 'vue'
import { defineStore } from 'pinia'

// 백엔드 프로필 API 구현 후 아래 import 주석을 해제함
// import { getUserProfile } from '@/api/userApi'

const DEFAULT_PROFILE_IMAGE = '/images/profiles/default-profile.svg'

export const useUserStore = defineStore('user', () => {
  // 백엔드 연동 전 상단바 확인을 위한 임시 사용자 데이터임
  const nickname = ref('username')
  const profileImageUrl = ref(DEFAULT_PROFILE_IMAGE)
  const pointBalance = ref(12450)
  const isLoading = ref(false)

  // 현재는 임시 데이터를 유지하며 백엔드 구현 후 아래 조회 코드를 활성화함
  const fetchUserProfile = async () => {
    /*
    // 중복 요청이 발생하지 않도록 이미 조회 중이면 함수를 종료함
    if (isLoading.value) {
      return
    }

    isLoading.value = true

    try {
      // GET /api/users/profile 응답을 조회함
      const response = await getUserProfile()
      const profile = response?.data

      // 조회된 사용자 정보를 상단바에서 공유하는 Pinia 상태에 저장함
      nickname.value = profile?.nickname || 'username'
      profileImageUrl.value = profile?.profileImageUrl || DEFAULT_PROFILE_IMAGE
      pointBalance.value = Number(profile?.pointBalance) || 0
    } catch (error) {
      // 4xx, 5xx 및 네트워크 오류 발생 시 사용자에게 실패 원인을 안내함
      alert(error.message || '사용자 정보를 불러오지 못했습니다.')
    } finally {
      // 성공 및 실패 여부와 관계없이 조회 상태를 해제함
      isLoading.value = false
    }
    */
  }

  // 프로필 이미지 로드 실패 시 고정 기본 이미지로 교체함
  const useDefaultProfileImage = () => {
    profileImageUrl.value = DEFAULT_PROFILE_IMAGE
  }

  return {
    nickname,
    profileImageUrl,
    pointBalance,
    isLoading,
    fetchUserProfile,
    useDefaultProfileImage,
  }
})
