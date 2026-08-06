import httpClient from "@/api/httpClient"
import { getApiErrorCode, getApiErrorMessage } from "@/commonUtils/apiError"

const createUserError = (error, fallbackMessage) => {
  const userError = new Error(getApiErrorMessage(error, fallbackMessage))
  userError.code = getApiErrorCode(error)
  userError.status = error.response?.status
  return userError
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
