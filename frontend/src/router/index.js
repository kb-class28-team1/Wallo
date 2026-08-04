import { createRouter, createWebHistory } from "vue-router"
import DefaultLayout from "@/layouts/DefaultLayout.vue"
import LandingView from "@/views/auth/LandingView.vue"
import LoginView from "@/views/auth/LoginView.vue"
import SignupView from "@/views/auth/SignupView.vue"
import AiAssistantView from "@/views/ai/AiAssistantView.vue"
import AssetView from "@/views/asset/AssetView.vue"
import ConnectionView from "@/views/asset/ConnectionView.vue"
import ExpenseHistoryView from "@/views/asset/ExpenseHistoryView.vue"
import ChallengeEntryView from "@/views/challenge/ChallengeEntryView.vue"
import ChallengeFeedView from "@/views/challenge/ChallengeFeedView.vue"
import ChallengeRankingView from "@/views/challenge/ChallengeRankingView.vue"
import MyChallengeView from "@/views/challenge/MyChallengeView.vue"
import MyFeedView from "@/views/challenge/MyFeedView.vue"
import DashboardView from "@/views/dashboard/DashboardView.vue"
import PointShopView from "@/views/product/PointShopView.vue"
import ReportListView from "@/views/report/ReportListView.vue"
import SettingsView from "@/views/user/SettingsView.vue"
import ConnectionManagementView from "@/views/user/ConnectionManagementView.vue"
import { useUserStore } from "@/stores/userStore"
import ChatView from "@/views/ChatView.vue"

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 서비스 최초 진입 시 랜딩 페이지를 표시하는 주소임
    {
      path: "/",
      name: "landing",
      component: LandingView,
    },
    // 로그인 페이지로 이동하는 주소임
    {
      path: "/login",
      name: "login",
      component: LoginView,
      meta: { guestOnly: true },
    },
    // 회원가입 페이지로 이동하는 주소임
    {
      path: "/signup",
      name: "signup",
      component: SignupView,
      meta: { guestOnly: true },
    },
    // 첫 로그인 사용자의 통합 자산 연결 페이지로 이동하는 주소임
    {
       path: "/connections/mydata",
       name: "connection",
       component: ConnectionView,
       meta: { requiresAuth: true },
    },
    // 로그인 이후 사이드바와 상단바를 공통으로 사용하는 페이지 그룹임
    {
      path: "/app",
      component: DefaultLayout,
      meta: { requiresAuth: true },
      children: [

        // 대시보드 페이지로 이동하는 주소임
        {
          path: "/dashboard",
          name: "dashboard",
          component: DashboardView,
        },
        // AI 컨설팅 페이지로 이동하는 주소임
        {
          path: "/ai-consulting",
          name: "ai-consulting",
          component: AiAssistantView,
        },
        {
          path: "/chat",
          name: "chat",
          component: ChatView,
        },
        // 자산 페이지로 이동하는 주소임
        {
          path: "/assets",
          name: "assets",
          component: AssetView,
        },
        {
          path: "/assets/expenses",
          name: "expenses",
          component: ExpenseHistoryView,
        },
        // 절약 챌린지의 피드 목록 페이지로 이동하는 주소임
        {
          path: "/challenges/current",
          name: "current-challenge",
          component: ChallengeEntryView,
        },
        // 선택한 챌린지의 피드 페이지로 이동하는 주소임
        {
          path: "/challenges/:challengeId/feeds",
          name: "challenge-feed",
          component: ChallengeFeedView,
        },
        // 절약 챌린지 주간랭킹 페이지로 이동하는 주소임
        {
          path: "/challenges/rankings/weekly",
          name: "weekly-ranking",
          component: ChallengeRankingView,
        },
        // 현재 사용자의 내 챌린지 페이지로 이동하는 주소임
        {
          path: "/users/me/challenge-dashboard",
          name: "my-challenge",
          component: MyChallengeView,
        },
        // 현재 사용자가 작성한 게시물 목록 페이지로 이동하는 주소임.
        {
          path: "/my-feeds",
          name: "my-feeds",
          component: MyFeedView,
        },
        // 포인트 샵 페이지로 이동하는 주소임
        {
          path: "/point-shop",
          name: "point-shop",
          component: PointShopView,
        },
        // 금융 리포트 페이지로 이동하는 주소임
        {
          path: "/reports",
          name: "reports",
          component: ReportListView,
        },
        // 사용자 설정 페이지로 이동하는 주소임
        {
          path: "/users/profile",
          name: "user-profile",
          component: SettingsView,
        },
        {
          path: "/users/profile/connections",
          name: "connection-management",
          component: ConnectionManagementView,
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (!userStore.hasCheckedAuth) {
    try {
      await userStore.restoreSession()
    } catch (error) {
      if (to.meta.requiresAuth) {
        return {
          name: "login",
          query: { redirect: to.fullPath },
        }
      }
    }
  }

  if (to.meta.requiresAuth && !userStore.isAuthenticated) {
    return {
      name: "login",
      query: { redirect: to.fullPath },
    }
  }

  if (
    to.meta.requiresAuth &&
    userStore.isAuthenticated &&
    !userStore.user?.connectionCompleted &&
    to.name !== "connection"
  ) {
    return { name: "connection", replace: true }
  }

  if (to.meta.guestOnly && userStore.isAuthenticated) {
    return {
      name: userStore.user?.connectionCompleted ? "dashboard" : "connection",
      replace: true,
    }
  }

  return true
})

export default router
