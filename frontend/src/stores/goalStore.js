import { ref } from "vue";
import { defineStore } from "pinia";
import {
  getAvailableGoalAccounts,
  getGoalRoadmap,
  getGoals,
  selectGoalAccount as selectGoalAccountRequest,
  updateGoalRoadmapStep,
} from "@/api/goalApi";
import { getApiErrorMessage } from "@/commonUtils/apiError";

const GOAL_STALE_TIME = 5 * 60 * 1000;
const ROADMAP_STALE_TIME = 5 * 60 * 1000;
const AVAILABLE_ACCOUNTS_STALE_TIME = 60 * 1000;

export const useGoalStore = defineStore("goal", () => {
  const goals = ref([]);
  const isLoading = ref(false);
  const initialLoading = ref(false);
  const refreshing = ref(false);
  const error = ref(null);
  const lastFetchedAt = ref(0);
  const hasFetchedGoals = ref(false);
  let goalsInFlight = null;
  let inFlightIncludesSync = false;
  let lastFetchIncludedSync = false;
  const availableAccounts = ref([]);
  const isAccountLoading = ref(false);
  const initialAccountLoading = ref(false);
  const refreshingAccounts = ref(false);
  const availableAccountsLastFetchedAt = ref(0);
  const hasFetchedAccounts = ref(false);
  let availableAccountsInFlight = null;
  const isAccountSaving = ref(false);
  const accountError = ref(null);
  const roadmap = ref(null)
  const isRoadmapLoading = ref(false)
  const roadmapGoalId = ref(null)
  const roadmapLastFetchedAt = ref(0)
  const roadmapRequests = new Map()
  const roadmapError = ref(null)
  const isRoadmapProgressSaving = ref(false)
  let sessionVersion = 0

  const fetchGoalRoadmap = (goalId, {
    notifyError = true,
    force = false,
    staleTime = ROADMAP_STALE_TIME,
  } = {}) => {
    if (!goalId) {
      roadmap.value = null
      roadmapGoalId.value = null
      roadmapLastFetchedAt.value = 0
      return null
    }

    const requestVersion = sessionVersion
    const requestKey = String(goalId)
    const inFlight = roadmapRequests.get(requestKey)
    if (inFlight) {
      return inFlight
    }

    const isFresh = (
      roadmapGoalId.value === goalId &&
      roadmapLastFetchedAt.value > 0 &&
      Date.now() - roadmapLastFetchedAt.value < staleTime
    )

    if (!force && isFresh) {
      return Promise.resolve(roadmap.value)
    }

    const isInitialLoad = roadmap.value === null || roadmapGoalId.value !== goalId
    isRoadmapLoading.value = true
    roadmapError.value = null

    let request
    request = (async () => {
      try {
        const response = await getGoalRoadmap(goalId)
        if (requestVersion !== sessionVersion) {
          return null
        }

        roadmap.value = response?.data ?? null
        roadmapGoalId.value = goalId
        roadmapLastFetchedAt.value = Date.now()
        return roadmap.value
      } catch (caughtError) {
        if (requestVersion !== sessionVersion) {
          return null
        }

        if (isInitialLoad) {
          roadmap.value = null
          roadmapGoalId.value = null
          roadmapLastFetchedAt.value = 0
        }
        roadmapError.value = getApiErrorMessage(caughtError, "목표 로드맵을 불러오지 못했습니다.")
        if (notifyError) alert(roadmapError.value)
        return null
      } finally {
        if (requestVersion === sessionVersion) {
          isRoadmapLoading.value = false
        }
        if (roadmapRequests.get(requestKey) === request) {
          roadmapRequests.delete(requestKey)
        }
      }
    })()

    roadmapRequests.set(requestKey, request)
    return request
  }

  const saveRoadmapStep = async (goalId, stepNumber, completed) => {
    const requestVersion = sessionVersion
    isRoadmapProgressSaving.value = true
    roadmapError.value = null
    try {
      const response = await updateGoalRoadmapStep(goalId, stepNumber, completed)
      if (requestVersion !== sessionVersion) {
        return null
      }

      roadmap.value = response?.data ?? null
      roadmapGoalId.value = goalId
      roadmapLastFetchedAt.value = Date.now()
      return roadmap.value
    } catch (caughtError) {
      if (requestVersion !== sessionVersion) {
        return null
      }

      roadmapError.value = getApiErrorMessage(
        caughtError,
        "로드맵 진행 상태를 저장하지 못했습니다.",
      )
      alert(roadmapError.value)
      return null
    } finally {
      if (requestVersion === sessionVersion) {
        isRoadmapProgressSaving.value = false
      }
    }
  }

  const fetchGoals = ({
    notifyError = true,
    force = false,
    staleTime = GOAL_STALE_TIME,
    syncAccounts = true,
  } = {}) => {
    if (goalsInFlight && (!syncAccounts || inFlightIncludesSync)) {
      return goalsInFlight
    }

    const requestVersion = sessionVersion
    const isFresh = (
      lastFetchedAt.value > 0 &&
      Date.now() - lastFetchedAt.value < staleTime &&
      (!syncAccounts || lastFetchIncludedSync)
    )

    if (!force && isFresh) {
      return Promise.resolve(goals.value)
    }

    const isInitialLoad = !hasFetchedGoals.value
    initialLoading.value = isInitialLoad
    refreshing.value = !isInitialLoad
    isLoading.value = true
    error.value = null;

    let request
    inFlightIncludesSync = syncAccounts
    request = (async () => {
      try {
        const response = await getGoals({ syncAccounts });
        if (requestVersion !== sessionVersion) {
          return []
        }

        goals.value = Array.isArray(response?.data) ? response.data : [];
        lastFetchedAt.value = Date.now()
        lastFetchIncludedSync = syncAccounts
        hasFetchedGoals.value = true
        await fetchGoalRoadmap(goals.value[0]?.goalId, {
          notifyError: false,
          force,
        })

        if (requestVersion !== sessionVersion) {
          return []
        }

        return goals.value;
      } catch (caughtError) {
        if (requestVersion !== sessionVersion) {
          return []
        }

        if (isInitialLoad) {
          goals.value = [];
          lastFetchedAt.value = 0
          lastFetchIncludedSync = false
          hasFetchedGoals.value = false
        }
        error.value = getApiErrorMessage(
          caughtError,
          "확정된 목표를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
        );

        if (notifyError) {
          alert(error.value);
        }

        return [];
      } finally {
        if (requestVersion === sessionVersion) {
          initialLoading.value = false
          refreshing.value = false
          isLoading.value = false;
          if (goalsInFlight === request) {
            goalsInFlight = null
            inFlightIncludesSync = false
          }
        }
      }
    })()

    goalsInFlight = request
    return request
  };

  const invalidateAvailableAccounts = () => {
    availableAccounts.value = [];
    availableAccountsLastFetchedAt.value = 0;
    hasFetchedAccounts.value = false;
    accountError.value = null;
  };

  const fetchAvailableAccounts = ({
    notifyError = true,
    force = false,
    staleTime = AVAILABLE_ACCOUNTS_STALE_TIME,
  } = {}) => {
    if (availableAccountsInFlight) {
      return availableAccountsInFlight
    }

    const requestVersion = sessionVersion
    const isFresh = (
      availableAccountsLastFetchedAt.value > 0 &&
      Date.now() - availableAccountsLastFetchedAt.value < staleTime
    )

    if (!force && isFresh) {
      return Promise.resolve(availableAccounts.value)
    }

    const isInitialLoad = !hasFetchedAccounts.value
    initialAccountLoading.value = isInitialLoad
    refreshingAccounts.value = !isInitialLoad
    isAccountLoading.value = true;
    accountError.value = null;

    let request
    request = (async () => {
      try {
        const response = await getAvailableGoalAccounts();
        if (requestVersion !== sessionVersion) {
          return []
        }

        availableAccounts.value = Array.isArray(response?.data)
          ? response.data
          : [];
        availableAccountsLastFetchedAt.value = Date.now()
        hasFetchedAccounts.value = true

        return availableAccounts.value;
      } catch (caughtError) {
        if (requestVersion !== sessionVersion) {
          return []
        }

        if (isInitialLoad) {
          availableAccounts.value = [];
          availableAccountsLastFetchedAt.value = 0
          hasFetchedAccounts.value = false
        }
        accountError.value = getApiErrorMessage(
          caughtError,
          "목표에 사용할 수 있는 계좌를 불러오는 중 오류가 발생했습니다.",
        );

        if (notifyError) {
          alert(accountError.value);
        }

        return [];
      } finally {
        if (requestVersion === sessionVersion) {
          initialAccountLoading.value = false
          refreshingAccounts.value = false
          isAccountLoading.value = false;
          if (availableAccountsInFlight === request) {
            availableAccountsInFlight = null
          }
        }
      }
    })()

    availableAccountsInFlight = request
    return request
  };

  const saveGoalAccount = async (goalId, accountId) => {
    const requestVersion = sessionVersion
    isAccountSaving.value = true;
    accountError.value = null;

    try {
      const response = await selectGoalAccountRequest(goalId, accountId);
      if (requestVersion !== sessionVersion) {
        return null
      }

      const selectedAccount = response?.data ?? null;

      const locallyUpdatedAccounts = availableAccounts.value.map((account) => ({
        ...account,
        selected: account.accountId === selectedAccount?.accountId,
      }));
      availableAccounts.value = locallyUpdatedAccounts;
      availableAccountsLastFetchedAt.value = 0;

      const refreshedAccounts = await fetchAvailableAccounts({
        notifyError: false,
        force: true,
      });
      if (requestVersion !== sessionVersion) {
        return null
      }

      if (refreshedAccounts.length > 0) {
        availableAccounts.value = refreshedAccounts.map((account) => ({
          ...account,
          selected: account.accountId === selectedAccount?.accountId,
        }));
      } else {
        availableAccounts.value = locallyUpdatedAccounts;
        availableAccountsLastFetchedAt.value = Date.now();
      }
      lastFetchedAt.value = 0;
      lastFetchIncludedSync = false;

      return selectedAccount;
    } catch (caughtError) {
      if (requestVersion !== sessionVersion) {
        return null
      }

      accountError.value = getApiErrorMessage(
        caughtError,
        "목표 계좌를 저장하는 중 오류가 발생했습니다.",
      );
      alert(accountError.value);
      throw caughtError;
    } finally {
      if (requestVersion === sessionVersion) {
        isAccountSaving.value = false;
      }
    }
  };

  const reset = () => {
    sessionVersion += 1

    goals.value = []
    isLoading.value = false
    initialLoading.value = false
    refreshing.value = false
    error.value = null
    lastFetchedAt.value = 0
    hasFetchedGoals.value = false
    goalsInFlight = null
    inFlightIncludesSync = false
    lastFetchIncludedSync = false

    availableAccounts.value = []
    isAccountLoading.value = false
    initialAccountLoading.value = false
    refreshingAccounts.value = false
    availableAccountsLastFetchedAt.value = 0
    hasFetchedAccounts.value = false
    availableAccountsInFlight = null
    isAccountSaving.value = false
    accountError.value = null

    roadmap.value = null
    isRoadmapLoading.value = false
    roadmapGoalId.value = null
    roadmapLastFetchedAt.value = 0
    roadmapRequests.clear()
    roadmapError.value = null
    isRoadmapProgressSaving.value = false
  }

  return {
    goals,
    isLoading,
    initialLoading,
    refreshing,
    lastFetchedAt,
    error,
    fetchGoals,
    reset,
    availableAccounts,
    isAccountLoading,
    initialAccountLoading,
    refreshingAccounts,
    availableAccountsLastFetchedAt,
    isAccountSaving,
    accountError,
    roadmap,
    isRoadmapLoading,
    roadmapError,
    isRoadmapProgressSaving,
    fetchGoalRoadmap,
    saveRoadmapStep,
    invalidateAvailableAccounts,
    fetchAvailableAccounts,
    saveGoalAccount,
  };
});
