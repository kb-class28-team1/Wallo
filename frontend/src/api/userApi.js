import httpClient from "@/api/httpClient"
import { getApiErrorCode, getApiErrorMessage } from "@/commonUtils/apiError"

const createUserError = (error, fallbackMessage) => {
  const userError = new Error(getApiErrorMessage(error, fallbackMessage))
  userError.code = getApiErrorCode(error)
  userError.status = error.response?.status
  return userError
}

export const getProfile = async () => {
  try {
    const response = await httpClient.get("/api/users/profile")
    return response.data?.data ?? response.data
  } catch (error) {
    throw createUserError(error, "프로필 정보를 불러오지 못했습니다.")
  }
}

export const updateNickname = async (nickname) => {
  try {
    const response = await httpClient.patch("/api/users/profile/nickname", {
      nickname,
    })

    return response.data?.data ?? response.data
  } catch (error) {
    throw createUserError(error, "닉네임을 저장하지 못했습니다.")
  }
}

export const changePassword = async (passwords) => {
  try {
    const response = await httpClient.patch("/api/users/profile/password", passwords)
    return response.data?.data ?? response.data
  } catch (error) {
    throw createUserError(error, "비밀번호를 변경하지 못했습니다.")
  }
}

export const updateProfileImage = async (file) => {
  try {
    const formData = new FormData()
    formData.append("image", file)
    const response = await httpClient.patch("/api/users/profile/image", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    })

    return response.data?.data ?? response.data
  } catch (error) {
    throw createUserError(error, "프로필 이미지를 저장하지 못했습니다.")
  }
}

export const resetProfileImage = async () => {
  try {
    const response = await httpClient.delete("/api/users/profile/image")
    return response.data?.data ?? response.data
  } catch (error) {
    throw createUserError(error, "기본 프로필 이미지로 변경하지 못했습니다.")
  }
}
