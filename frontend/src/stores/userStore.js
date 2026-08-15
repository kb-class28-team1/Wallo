import { computed, ref } from "vue"
import { defineStore } from "pinia"
import { login as loginRequest, logout as logoutRequest, refreshAccessToken } from "@/api/authApi"
import {
  changePassword as changePasswordRequest,
  getProfile as getProfileRequest,
  resetProfileImage as resetProfileImageRequest,
  updateNickname as updateNicknameRequest,
  updateProfileImage as updateProfileImageRequest,
} from "@/api/userApi"
import {
  getCachedResource,
  getResource,
  hasInFlightResource,
  invalidateResource,
} from "@/utils/resourceCache"

const DEFAULT_PROFILE_IMAGE = "/images/profiles/default-profile.svg"
const PROFILE_CACHE_KEY = "user:profile:current"
const PROFILE_STALE_TIME = 60 * 1000

export const useUserStore = defineStore("user", () => {
  const user = ref(null)
  const isLoading = ref(false)
  const hasCheckedAuth = ref(false)
  const initialProfileLoading = ref(false)
  const refreshingProfile = ref(false)
  const hasLoadedProfile = ref(false)
  let profileRequest = null

  const isAuthenticated = computed(() => Boolean(user.value?.id))
  const nickname = computed(() => user.value?.nickname || "")
  const profileImageUrl = computed(() => user.value?.profileImageUrl || DEFAULT_PROFILE_IMAGE)
  const pointBalance = computed(() => Number(user.value?.point) || 0)

  const invalidateProfileCache = () => invalidateResource(PROFILE_CACHE_KEY)

  const setUser = (authenticatedUser) => {
    user.value = authenticatedUser
    hasCheckedAuth.value = true
    hasLoadedProfile.value = Boolean(authenticatedUser?.id)
  }

  // 포인트를 사용하거나 보상받은 직후 공용 상단바도 같은 잔액을 표시하도록 갱신함.
  const updatePointBalance = (point) => {
    if (!user.value) {
      return
    }

    invalidateProfileCache()
    user.value = {
      ...user.value,
      point: Number(point) || 0,
    }
  }

  const clearAuth = () => {
    invalidateProfileCache()
    user.value = null
    hasCheckedAuth.value = true
    hasLoadedProfile.value = false
  }

  const login = async (credentials) => {
    isLoading.value = true
    try {
      const authResponse = await loginRequest(credentials)
      invalidateProfileCache()
      setUser(authResponse.user)
      return authResponse.user
    } finally {
      isLoading.value = false
    }
  }

  const restoreSession = async (force = false) => {
    if (hasCheckedAuth.value && !force) {
      return isAuthenticated.value
    }

    isLoading.value = true
    try {
      const tokenResponse = await refreshAccessToken()
      invalidateProfileCache()
      setUser(tokenResponse.user)
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

  const applyProfile = (profile) => {
    if (user.value) {
      user.value = {
        ...user.value,
        ...profile,
      }
    } else {
      setUser({
        ...profile,
        point: 0,
        connectionCompleted: false,
      })
    }

    hasLoadedProfile.value = true
    return profile
  }

  const fetchProfile = async ({ force = false } = {}) => {
    const cached =
      !force && !hasInFlightResource(PROFILE_CACHE_KEY)
        ? getCachedResource(PROFILE_CACHE_KEY, { staleTime: PROFILE_STALE_TIME })
        : undefined

    if (cached !== undefined) {
      initialProfileLoading.value = false
      refreshingProfile.value = false
      return applyProfile(cached)
    }

    if (profileRequest) {
      return profileRequest
    }

    const isInitialLoad = !hasLoadedProfile.value
    initialProfileLoading.value = isInitialLoad
    refreshingProfile.value = !isInitialLoad
    isLoading.value = true

    const request = getProfileRequest()
    const currentRequest = getResource(PROFILE_CACHE_KEY, () => request, {
      force,
      staleTime: PROFILE_STALE_TIME,
    })
    profileRequest = currentRequest

    try {
      const profile = await profileRequest
      return applyProfile(profile)
    } finally {
      if (profileRequest === currentRequest) {
        profileRequest = null
      }
      initialProfileLoading.value = false
      refreshingProfile.value = false
      isLoading.value = false
    }
  }

  const updateNickname = async (nickname) => {
    isLoading.value = true
    try {
      const updatedProfile = await updateNicknameRequest(nickname)
      const updatedNickname = updatedProfile?.nickname || nickname

      if (user.value) {
        user.value = {
          ...user.value,
          nickname: updatedNickname,
        }
      }
      invalidateProfileCache()

      return updatedNickname
    } finally {
      isLoading.value = false
    }
  }

  const changePassword = async (passwords) => {
    isLoading.value = true
    try {
      await changePasswordRequest(passwords)
    } finally {
      isLoading.value = false
    }
  }

  const updateProfileImage = async (file) => {
    isLoading.value = true
    try {
      const updatedProfile = await updateProfileImageRequest(file)
      const updatedImageUrl = updatedProfile?.profileImageUrl || DEFAULT_PROFILE_IMAGE

      if (user.value) {
        user.value = {
          ...user.value,
          profileImageUrl: updatedImageUrl,
        }
      }
      invalidateProfileCache()

      return updatedImageUrl
    } finally {
      isLoading.value = false
    }
  }

  const resetProfileImage = async () => {
    isLoading.value = true
    try {
      const updatedProfile = await resetProfileImageRequest()
      const updatedImageUrl = updatedProfile?.profileImageUrl || DEFAULT_PROFILE_IMAGE

      if (user.value) {
        user.value = {
          ...user.value,
          profileImageUrl: updatedImageUrl,
        }
      }
      invalidateProfileCache()

      return updatedImageUrl
    } finally {
      isLoading.value = false
    }
  }

  const fetchUserProfile = async () => {
    try {
      return await fetchProfile()
    } catch (error) {
      if (error.status === 401) {
        clearAuth()
        return false
      }
      alert(error.message || "사용자 정보를 불러오지 못했습니다.")
      return false
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
    initialProfileLoading,
    refreshingProfile,
    hasLoadedProfile,
    hasCheckedAuth,
    isAuthenticated,
    login,
    logout,
    updateNickname,
    changePassword,
    updateProfileImage,
    resetProfileImage,
    restoreSession,
    fetchProfile,
    clearAuth,
    updatePointBalance,
    fetchUserProfile,
    useDefaultProfileImage,
  }
})
