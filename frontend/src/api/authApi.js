import httpClient from "./httpClient"

const getErrorMessage = (error, fallbackMessage) =>
  error.response?.data?.error?.message ||
  error.response?.data?.message ||
  fallbackMessage

const createAuthError = (error, fallbackMessage) => {
  const authError = new Error(getErrorMessage(error, fallbackMessage))
  authError.code = error.response?.data?.code
  authError.status = error.response?.status
  return authError
}

export const signup = async (payload) => {
  try {
    const response = await httpClient.post("/api/auth/signup", payload)
    return response.data
  } catch (error) {
    throw createAuthError(error, "회원가입에 실패했습니다.")
  }
}

export const login = async (payload) => {
  try {
    const response = await httpClient.post("/api/auth/login", payload)
    return response.data
  } catch (error) {
    throw createAuthError(error, "로그인에 실패했습니다.")
  }
}

export const getCurrentUser = async () => {
  try {
    const response = await httpClient.get("/api/auth/me")
    return response.data
  } catch (error) {
    const authError = new Error(getErrorMessage(error, "로그인 정보를 확인하지 못했습니다."))
    authError.status = error.response?.status
    throw authError
  }
}

export const logout = async () => {
  try {
    const response = await httpClient.post("/api/auth/logout")
    return response.data
  } catch (error) {
    throw new Error(getErrorMessage(error, "로그아웃에 실패했습니다."))
  }
}
