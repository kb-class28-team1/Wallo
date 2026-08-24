import { defineStore } from "pinia"
import { getAssets, syncAssets as requestAssetSync } from "@/api/assetApi"
import { useFinancialInvalidationStore } from "@/stores/financialInvalidationStore"
import { getApiErrorMessage } from "@/utils/apiError"

const ASSET_STALE_TIME = 30 * 1000
const requestStateByStore = new WeakMap()

const getRequestState = (store) => {
  if (!requestStateByStore.has(store)) {
    requestStateByStore.set(store, { inFlight: null, inFlightRevision: null })
  }

  return requestStateByStore.get(store)
}

export const useAssetStore = defineStore("asset", {
  state: () => ({
    initialLoading: false,
    refreshing: false,
    assets: null,
    error: null,
    lastFetchedAt: 0,
    hasFetched: false,
    lastFetchedRevision: 0,
    isSyncing: false,
    lastSyncResult: null,
    syncError: null,
  }),

  getters: {
    isAssetLoading: (state) => state.initialLoading || state.refreshing,
    isLoading: (state) => state.initialLoading || state.refreshing,
  },

  actions: {
    invalidateAssetsCache() {
      this.lastFetchedAt = 0
      useFinancialInvalidationStore().markChanged()
    },

    fetchAssets({ notifyError = true, force = false, staleTime = ASSET_STALE_TIME } = {}) {
      const invalidationStore = useFinancialInvalidationStore()
      const currentRevision = invalidationStore.revision
      const requestState = getRequestState(this)

      if (requestState.inFlight && requestState.inFlightRevision === currentRevision) {
        return requestState.inFlight
      }

      const isFresh =
        this.lastFetchedRevision === currentRevision &&
        this.lastFetchedAt > 0 &&
        Date.now() - this.lastFetchedAt < staleTime

      if (!force && isFresh) {
        return Promise.resolve(this.assets)
      }

      const isInitialLoad = !this.hasFetched
      this.initialLoading = isInitialLoad
      this.refreshing = !isInitialLoad
      this.error = null
      const requestRevision = currentRevision

      let request
      request = (async () => {
        try {
          const response = await getAssets()
          if (requestRevision !== invalidationStore.revision) {
            return this.assets
          }

          this.assets = response?.data ?? null
          this.lastFetchedAt = Date.now()
          this.lastFetchedRevision = requestRevision
          this.hasFetched = true

          return this.assets
        } catch (error) {
          if (requestRevision !== invalidationStore.revision) {
            return this.assets
          }

          const isUnauthorized = error.response?.status === 401
          const errorMessage = getApiErrorMessage(
            error,
            isUnauthorized
              ? "로그인이 만료되었습니다. 다시 로그인해 주세요."
              : "자산 정보를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
          )

          if (isInitialLoad) {
            this.assets = null
            this.hasFetched = false
          }
          this.error = errorMessage
          if (notifyError) {
            alert(errorMessage)
          }

          throw error
        } finally {
          if (requestState.inFlight === request) {
            this.initialLoading = false
            this.refreshing = false
            requestState.inFlight = null
            requestState.inFlightRevision = null
          }
        }
      })()

      requestState.inFlight = request
      requestState.inFlightRevision = requestRevision
      return request
    },

    async syncAssets({ notifyError = true } = {}) {
      if (this.isSyncing) {
        return null
      }

      this.isSyncing = true
      this.syncError = null

      try {
        const response = await requestAssetSync()
        if (!response?.success || !response?.data) {
          throw new Error(response?.error?.message || "자산 거래내역 동기화에 실패했습니다.")
        }

        this.lastSyncResult = response.data
        this.invalidateAssetsCache()

        try {
          await this.fetchAssets({
            notifyError: false,
            force: true,
          })
        } catch {
          // 동기화 결과는 유지하고, 화면에서 자산 재조회 오류를 별도로 처리합니다.
        }

        return this.lastSyncResult
      } catch (error) {
        const errorMessage = getApiErrorMessage(
          error,
          "자산 거래내역 동기화에 실패했습니다. 잠시 후 다시 시도해 주세요.",
        )
        this.syncError = errorMessage
        if (notifyError) {
          alert(errorMessage)
        }

        throw error
      } finally {
        this.isSyncing = false
      }
    },
  },
})
