import { computed, ref } from "vue"
import { defineStore } from "pinia"
import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
} from "@/api/authApi"

const DEFAULT_PROFILE_IMAGE = "/images/profiles/default-profile.svg"

export const useUserStore = defineStore("user", () => {
  const user = ref(null)
  const isLoading = ref(false)
  const hasCheckedAuth = ref(false)

  const isAuthenticated = computed(() => Boolean(user.value?.id))
  const nickname = computed(() => user.value?.nickname || "")
  const profileImageUrl = computed(
    () => user.value?.profileImageUrl || DEFAULT_PROFILE_IMAGE,
  )
  const pointBalance = computed(() => Number(user.value?.point) || 0)

  const setUser = (authenticatedUser) => {
    user.value = authenticatedUser
    hasCheckedAuth.value = true
  }

  const clearAuth = () => {
    user.value = null
    hasCheckedAuth.value = true
  }

  const login = async (credentials) => {
    isLoading.value = true
    try {
      const authenticatedUser = await loginRequest(credentials)
      setUser(authenticatedUser)
      return authenticatedUser
    } finally {
      isLoading.value = false
    }
  }

  const restoreSession = async () => {
    if (hasCheckedAuth.value) {
      return isAuthenticated.value
    }

    isLoading.value = true
    try {
      setUser(await getCurrentUser())
      return true
    } catch (error) {
      clearAuth()
      if (error.status !== 401) {
        throw error
      }
      return false
    } finally {
      isLoading.value = false
    }
  }

  const logout = async () => {
    isLoading.value = true
    try {
      await logoutRequest()
      clearAuth()
    } finally {
      isLoading.value = false
    }
  }

  const fetchUserProfile = async () => {
    try {
      await restoreSession()
    } catch (error) {
      alert(error.message || "사용자 정보를 불러오지 못했습니다.")
    }
  }

  const useDefaultProfileImage = (event) => {
    if (event?.target) {
      event.target.src = DEFAULT_PROFILE_IMAGE
    }
  }

  return {
    user,
    nickname,
    profileImageUrl,
    pointBalance,
    isLoading,
    hasCheckedAuth,
    isAuthenticated,
    login,
    logout,
    restoreSession,
    clearAuth,
    fetchUserProfile,
    useDefaultProfileImage,
  }
})
