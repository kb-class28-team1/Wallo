import { computed, ref } from "vue"
import { defineStore } from "pinia"
import {
  completeSelfCheckMission as completeSelfCheckMissionRequest,
  generateNextDayMissions as generateNextDayMissionsRequest,
  getTodayMissions,
  verifyMissionWithFeed as verifyMissionWithFeedRequest,
  verifyTransactionMission as verifyTransactionMissionRequest,
} from "@/api/missionApi"
import { useToastStore } from "@/stores/toastStore"

export const MISSION_POLL_INTERVAL_MS = 2500
export const MAX_MISSION_POLL_ATTEMPTS = 48
export const MISSION_STALE_TIME_MS = 60 * 1000
export const MISSION_GENERATION_FAILED_STATUS = "GENERATION_FAILED"
export const MISSION_WAITING_STATUS = "WAITING_ANALYSIS"
export const MISSION_RATE_LIMIT_MESSAGE = "AI 사용량 제한으로 잠시 후 다시 생성됩니다."

const POLLING_STATUSES = new Set([
  MISSION_WAITING_STATUS,
  MISSION_GENERATION_FAILED_STATUS,
])

export const useMissionStore = defineStore("mission", () => {
  const missions = ref([])
  const status = ref("READY")
  const failureReason = ref(null)
  const error = ref("")
  const isLoading = ref(false)
  const isPolling = ref(false)
  const lastFetchedAt = ref(0)

  const toastStore = useToastStore()
  const isFresh = computed(
    () =>
      lastFetchedAt.value > 0 &&
      Date.now() - lastFetchedAt.value < MISSION_STALE_TIME_MS,
  )

  let inFlightRequest = null
  let pollingTimer = null
  let pollingAttempts = 0
  let missionDateTimer = null
  let lifecycleStarted = false
  let sessionVersion = 0
  let failureNotified = false

  const getSnapshot = () => ({
    status: status.value,
    failureReason: failureReason.value,
    missions: missions.value,
  })

  const applyMissionResponse = (response) => {
    status.value = response?.status || "READY"
    missions.value = Array.isArray(response?.missions) ? response.missions : []
    failureReason.value = response?.failureReason || null
    error.value = ""

    if (status.value === MISSION_GENERATION_FAILED_STATUS) {
      if (!failureNotified) {
        toastStore.show(
          failureReason.value === "RATE_LIMIT"
            ? MISSION_RATE_LIMIT_MESSAGE
            : "오늘의 미션을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        )
        failureNotified = true
      }
    } else {
      failureNotified = false
    }
  }

  const stopPolling = () => {
    if (pollingTimer) {
      clearInterval(pollingTimer)
      pollingTimer = null
    }
    pollingAttempts = 0
    isPolling.value = false
  }

  const startPolling = () => {
    if (pollingTimer || !POLLING_STATUSES.has(status.value)) return

    pollingAttempts = 0
    isPolling.value = true
    pollingTimer = setInterval(async () => {
      pollingAttempts += 1

      try {
        await fetchTodayMissions({ force: true, notifyError: false })
      } catch {
        // 백그라운드 폴링 오류는 다음 시도에서 다시 확인함.
      }

      if (
        !POLLING_STATUSES.has(status.value) ||
        pollingAttempts >= MAX_MISSION_POLL_ATTEMPTS
      ) {
        stopPolling()
      }
    }, MISSION_POLL_INTERVAL_MS)
  }

  const syncPolling = () => {
    if (POLLING_STATUSES.has(status.value)) {
      startPolling()
    } else {
      stopPolling()
    }
  }

  const fetchTodayMissions = ({ force = false, notifyError = false } = {}) => {
    if (!force && isFresh.value) {
      return Promise.resolve(getSnapshot())
    }

    if (inFlightRequest) return inFlightRequest

    const requestVersion = sessionVersion
    isLoading.value = true
    error.value = ""

    let request
    request = (async () => {
      try {
        const response = await getTodayMissions()

        if (requestVersion !== sessionVersion) return getSnapshot()

        applyMissionResponse(response)
        lastFetchedAt.value = Date.now()
        syncPolling()
        return response
      } catch (caughtError) {
        if (requestVersion !== sessionVersion) throw caughtError

        const isRateLimited = caughtError?.status === 429
        status.value = isRateLimited ? MISSION_GENERATION_FAILED_STATUS : "ERROR"
        missions.value = []
        failureReason.value = isRateLimited ? "RATE_LIMIT" : null
        error.value = caughtError?.message || "오늘의 미션을 불러오지 못했습니다."

        if (isRateLimited) {
          if (!failureNotified) {
            toastStore.show(MISSION_RATE_LIMIT_MESSAGE)
            failureNotified = true
          }
          syncPolling()
        } else if (notifyError) {
          alert(error.value)
        }

        throw caughtError
      } finally {
        if (requestVersion === sessionVersion) {
          isLoading.value = false
        }
        if (inFlightRequest === request) {
          inFlightRequest = null
        }
      }
    })()

    inFlightRequest = request
    return request
  }

  const refreshTodayMissions = async ({
    force = false,
    notifyError = false,
    verifyTransactions = false,
  } = {}) => {
    const response = await fetchTodayMissions({ force, notifyError })
    if (!verifyTransactions) return { response, verificationResults: [] }

    const transactionMissions = missions.value.filter(
      (mission) => mission.verificationType === "TRANSACTION" && !mission.completed,
    )
    if (!transactionMissions.length) {
      return { response, verificationResults: [] }
    }

    isLoading.value = true
    try {
      const verificationResults = await Promise.allSettled(
        transactionMissions.map((mission) => verifyTransactionMissionRequest(mission.id)),
      )
      const hasPassedMission = verificationResults.some(
        (result) => result.status === "fulfilled" && result.value?.decision === "PASS",
      )
      const refreshedResponse = hasPassedMission
        ? await fetchTodayMissions({ force: true, notifyError: false })
        : response

      return { response: refreshedResponse, verificationResults }
    } finally {
      isLoading.value = false
    }
  }

  const refreshIfStale = ({ notifyError = false } = {}) =>
    fetchTodayMissions({ notifyError })

  const handleMissionUpdated = async (event) => {
    const missionResponse = event?.detail?.missionResponse
    if (missionResponse) {
      applyMissionResponse(missionResponse)
      lastFetchedAt.value = Date.now()
      syncPolling()
      return missionResponse
    }

    try {
      return await fetchTodayMissions({ force: true, notifyError: false })
    } catch {
      return null
    }
  }

  const handlePageVisibility = () => {
    if (document.visibilityState === "visible") {
      void refreshIfStale({ notifyError: false }).catch(() => {})
    }
  }

  const scheduleNextMissionDateRefresh = () => {
    if (missionDateTimer) clearTimeout(missionDateTimer)

    const now = new Date()
    const nextDate = new Date(now)
    nextDate.setHours(24, 0, 0, 250)
    missionDateTimer = setTimeout(() => {
      void fetchTodayMissions({ force: true, notifyError: false }).catch(() => {})
      scheduleNextMissionDateRefresh()
    }, Math.max(0, nextDate.getTime() - now.getTime()))
  }

  const startLifecycle = () => {
    if (lifecycleStarted || typeof window === "undefined") return

    lifecycleStarted = true
    window.addEventListener("wallo:mission-updated", handleMissionUpdated)
    window.addEventListener("focus", handlePageVisibility)
    document.addEventListener("visibilitychange", handlePageVisibility)
    void fetchTodayMissions({ notifyError: true }).catch(() => {})
    scheduleNextMissionDateRefresh()
  }

  const stopLifecycle = () => {
    if (!lifecycleStarted || typeof window === "undefined") return

    window.removeEventListener("wallo:mission-updated", handleMissionUpdated)
    window.removeEventListener("focus", handlePageVisibility)
    document.removeEventListener("visibilitychange", handlePageVisibility)
    if (missionDateTimer) {
      clearTimeout(missionDateTimer)
      missionDateTimer = null
    }
    stopPolling()
    lifecycleStarted = false
  }

  const completeSelfCheckMission = async (dailyMissionId) => {
    try {
      const response = await completeSelfCheckMissionRequest(dailyMissionId)
      await fetchTodayMissions({ force: true, notifyError: false })
      return response
    } catch (caughtError) {
      error.value = caughtError?.message || "미션 완료 처리에 실패했습니다."
      throw caughtError
    }
  }

  const verifyMissionWithFeed = async (dailyMissionId, feedId) => {
    try {
      const response = await verifyMissionWithFeedRequest(dailyMissionId, feedId)
      await fetchTodayMissions({ force: true, notifyError: false })
      return response
    } catch (caughtError) {
      error.value = caughtError?.message || "미션 인증에 실패했습니다."
      throw caughtError
    }
  }

  const generateNextDayMissions = async () => {
    try {
      const response = await generateNextDayMissionsRequest()
      applyMissionResponse(response)
      lastFetchedAt.value = Date.now()
      syncPolling()
      return response
    } catch (caughtError) {
      status.value = "ERROR"
      error.value = caughtError?.message || "다음날 미션 생성에 실패했습니다."
      throw caughtError
    }
  }

  const reset = () => {
    sessionVersion += 1
    missions.value = []
    status.value = "READY"
    failureReason.value = null
    error.value = ""
    isLoading.value = false
    lastFetchedAt.value = 0
    failureNotified = false
    inFlightRequest = null
    stopPolling()
  }

  return {
    missions,
    status,
    failureReason,
    error,
    isLoading,
    isPolling,
    lastFetchedAt,
    isFresh,
    fetchTodayMissions,
    refreshTodayMissions,
    refreshIfStale,
    handleMissionUpdated,
    startPolling,
    stopPolling,
    startLifecycle,
    stopLifecycle,
    completeSelfCheckMission,
    verifyMissionWithFeed,
    generateNextDayMissions,
    applyMissionResponse,
    reset,
  }
})
