import axios from "axios";
import { clearAccessToken, getAccessToken, setAccessToken } from "./authToken"

const TIMING_LOG_PREFIX = "[WALLO_TIMING]"

const createRequestId = () => {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `wallo-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const isGoalFlowRequest = (url = "") => {
  const normalizedUrl = String(url).split("?")[0]
  return normalizedUrl.includes("/api/conversations")
    || normalizedUrl.includes("/api/goals")
}

const timingNow = () => (
  typeof performance !== "undefined" ? performance.now() : Date.now()
)

const getRequestId = (config) => {
  if (config?._walloRequestId) return config._walloRequestId

  const headers = config?.headers
  if (!headers) return null
  if (typeof headers.get === "function") {
    return headers.get("X-Request-Id") || headers.get("x-request-id") || null
  }
  return headers["X-Request-Id"] || headers["x-request-id"] || null
}

const setRequestId = (config, requestId) => {
  config._walloRequestId = requestId
  config.headers = config.headers || {}
  if (typeof config.headers.set === "function") {
    config.headers.set("X-Request-Id", requestId)
  } else {
    config.headers["X-Request-Id"] = requestId
  }
}

const ensureRequestId = (config) => {
  const requestId = getRequestId(config) || createRequestId()
  setRequestId(config, requestId)
  return requestId
}

const logRequestStart = (config) => {
  if (!isGoalFlowRequest(config?.url)) return

  config._walloTimingStartedAt = timingNow()
  config._walloRequestId = getRequestId(config)
  console.info(`${TIMING_LOG_PREFIX} request.start`, {
    method: config.method?.toUpperCase(),
    url: config.url,
    requestId: config._walloRequestId,
  })
}

const logRequestEnd = (config, { status, failed = false } = {}) => {
  const startedAt = config?._walloTimingStartedAt
  if (startedAt === undefined || !isGoalFlowRequest(config?.url)) return

  console[failed ? "warn" : "info"](`${TIMING_LOG_PREFIX} request.end`, {
    method: config.method?.toUpperCase(),
    url: config.url,
    status,
    requestId: config._walloRequestId || getRequestId(config),
    elapsedMs: Math.round(timingNow() - startedAt),
  })
}

const httpClient = axios.create({
  baseURL: "/",
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

let unauthorizedHandler = null

export const setUnauthorizedHandler = (handler) => {
  unauthorizedHandler = handler
}

httpClient.interceptors.response.use(
  (response) => {
    logRequestEnd(response.config, { status: response.status })
    return response
  },
  async (error) => {
    logRequestEnd(error.config, {
      status: error.response?.status,
      failed: true,
    })
    const requestUrl = error.config?.url || ""
    const isLoginRequest = requestUrl.includes("/api/auth/login")
    const isSignupRequest = requestUrl.includes("/api/auth/signup")
    const isSessionCheck = requestUrl.includes("/api/auth/me")
    const isRefreshRequest = requestUrl.includes("/api/auth/refresh")
    const isLogoutRequest = requestUrl.includes("/api/auth/logout")

    if (
      error.response?.status === 401 &&
      !error.config?._retry &&
      !isLoginRequest &&
      !isSignupRequest &&
      !isSessionCheck &&
      !isRefreshRequest &&
      !isLogoutRequest
    ) {
      error.config._retry = true
      try {
        const refreshRequestId = createRequestId()
        const refreshResponse = await axios.post("/api/auth/refresh", null, {
          withCredentials: true,
          headers: { "X-Request-Id": refreshRequestId },
        })
        setAccessToken(refreshResponse.data?.accessToken)
        error.config.headers = error.config.headers || {}
        error.config.headers.Authorization = `Bearer ${getAccessToken()}`
        return httpClient.request(error.config)
      } catch (refreshError) {
        clearAccessToken()
      }
    }

    if (
      error.response?.status === 401 &&
      !isLoginRequest &&
      !isSignupRequest &&
      !isSessionCheck &&
      !isRefreshRequest &&
      unauthorizedHandler
    ) {
      unauthorizedHandler()
    }

    return Promise.reject(error)
  },
)

httpClient.interceptors.request.use((config) => {
  ensureRequestId(config)
  logRequestStart(config)
  const token = getAccessToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export default httpClient;
