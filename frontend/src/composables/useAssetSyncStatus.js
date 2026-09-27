import { ref, unref } from "vue"

export const useAssetSyncStatus = ({
  isSyncing,
  syncError,
  syncAssets,
  reload,
  reloadError,
  reloadFailureMessage,
  beforeSync = () => {},
}) => {
  const syncStatus = ref(null)

  const syncCurrentMonth = async () => {
    if (unref(isSyncing)) return

    syncStatus.value = null
    beforeSync()

    try {
      const result = await syncAssets()
      if (!result) return

      await reload()
      if (unref(reloadError)) {
        syncStatus.value = {
          type: "warning",
          message: reloadFailureMessage,
        }
        return
      }

      const failedConnections = Number(result.failedConnections) || 0
      const summary = `신규 ${Number(result.inserted) || 0}건, 수정 ${Number(result.updated) || 0}건`
      syncStatus.value =
        failedConnections > 0
          ? {
              type: "warning",
              message: `동기화가 완료되었습니다. ${summary}, 실패한 연결기관 ${failedConnections}건`,
            }
          : {
              type: "success",
              message: `동기화가 완료되었습니다. ${summary}`,
            }
    } catch {
      syncStatus.value = {
        type: "danger",
        message: unref(syncError) || "자산 거래내역 동기화에 실패했습니다.",
      }
    }
  }

  return { syncStatus, syncCurrentMonth }
}
