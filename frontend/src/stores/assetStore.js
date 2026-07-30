import { defineStore } from "pinia";
import { connectAllAssets } from "@/api/assetApi";

export const useAssetStore = defineStore("asset", {
  state: () => ({
    isLoading: false,
    connectionResults: [],
  }),

  actions: {
    async executeConnection(consentAgreed) {
      this.isLoading = true;

      try {
        const response = await connectAllAssets(consentAgreed);
        this.connectionResults = response?.data?.results ?? [];

        return response;
      } catch (error) {
        this.connectionResults = [];

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


