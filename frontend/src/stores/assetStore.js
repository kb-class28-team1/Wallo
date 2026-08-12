import { defineStore } from "pinia";
import { getAssets, syncAssets as requestAssetSync } from "@/api/assetApi";
import { getApiErrorMessage } from "@/commonUtils/apiError";

export const useAssetStore = defineStore("asset", {
  state: () => ({
    isAssetLoading: false,
    assets: null,
    error: null,
    isSyncing: false,
    lastSyncResult: null,
    syncError: null,
  }),

  actions: {
    async fetchAssets({ notifyError = true } = {}) {
      this.isAssetLoading = true;
      this.error = null;

      try {
        const response = await getAssets();
        this.assets = response?.data ?? null;

        return this.assets;
      } catch (error) {
        const isUnauthorized = error.response?.status === 401;
        const errorMessage = getApiErrorMessage(
          error,
          isUnauthorized
            ? "로그인이 만료되었습니다. 다시 로그인해 주세요."
            : "자산 정보를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
        );

        this.assets = null;
        this.error = errorMessage;
        if (notifyError) {
          alert(errorMessage);
        }

        throw error;
      } finally {
        this.isAssetLoading = false;
      }
    },

    async syncAssets({ notifyError = true } = {}) {
      if (this.isSyncing) {
        return null;
      }

      this.isSyncing = true;
      this.syncError = null;

      try {
        const response = await requestAssetSync();
        if (!response?.success || !response?.data) {
          throw new Error(
            response?.error?.message
              || "?먯궛 嫄곕옒 ?숈뿉 ?ㅽ뙣?덉뒿?덈떎.",
          );
        }

        this.lastSyncResult = response.data;
        return this.lastSyncResult;
      } catch (error) {
        const errorMessage = getApiErrorMessage(
          error,
          "?먯궛 嫄곕옒 ?숈뿉 ?ㅽ뙣?덉뒿?덈떎. ?좎떆 ???ㅼ떆 ?쒕룄??二쇱꽭??",
        );
        this.syncError = errorMessage;
        if (notifyError) {
          alert(errorMessage);
        }

        throw error;
      } finally {
        this.isSyncing = false;
      }
    },
  },
});

