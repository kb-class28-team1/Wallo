import { defineStore } from "pinia";
import { connectAllAssets, getAssets } from "@/api/assetApi";

const getErrorMessage = (error) =>
  error.response?.data?.error?.message ||
  error.message ||
  "자산 정보를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

export const useAssetStore = defineStore("asset", {
  state: () => ({
    isLoading: false,
    isAssetLoading: false,
    assets: null,
    error: null,
    connectionResults: [],
  }),

  actions: {
    async fetchAssets() {
      this.isAssetLoading = true;
      this.error = null;

      try {
        const response = await getAssets();
        this.assets = response?.data ?? null;

        return this.assets;
      } catch (error) {
        const errorMessage = getErrorMessage(error);

        this.assets = null;
        this.error = errorMessage;
        alert(errorMessage);

        throw error;
      } finally {
        this.isAssetLoading = false;
      }
    },

    async executeConnection(consentAgreed) {
      this.isLoading = true;
      this.error = null;

      try {
        const response = await connectAllAssets(consentAgreed);
        this.connectionResults = response?.data?.results ?? [];

        return response;
      } catch (error) {
        this.connectionResults = [];
        this.error = getErrorMessage(error);

        const status = error.response?.status;
        const errorMessage = error.response?.data?.error?.message;

        if (status === 400) {
          alert(errorMessage || "필수 약관에 동의해야 자산 연동을 진행할 수 있습니다.");
        } else if (status === 401) {
          alert(errorMessage || "로그인이 만료되었습니다. 다시 로그인해 주세요.");
        } else {
          alert(errorMessage || "자산 연동 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }

        throw error;
      } finally {
        this.isLoading = false;
      }
    },
  },
});


