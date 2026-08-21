import { createRouter, createWebHistory } from "vue-router"
import { useUserStore } from "@/stores/userStore"

const LandingView = () => import("@/views/auth/LandingView.vue")
const LoginView = () => import("@/views/auth/LoginView.vue")
const SignupView = () => import("@/views/auth/SignupView.vue")
const OnboardingWelcomeView = () => import("@/views/onboarding/OnboardingWelcomeView.vue")
const AiAssistantView = () => import("@/views/ai/AiAssistantView.vue")
const AnalysisDashboardView = () => import("@/views/analysis/AnalysisDashboardView.vue")
const AssetView = () => import("@/views/asset/AssetView.vue")
const ConnectionView = () => import("@/views/asset/ConnectionView.vue")
const ExpenseHistoryView = () => import("@/views/asset/ExpenseHistoryView.vue")
const CategoryExpenseView = () => import("@/views/asset/CategoryExpenseView.vue")
const ChallengeEntryView = () => import("@/views/challenge/ChallengeEntryView.vue")
const ChallengeFeedView = () => import("@/views/challenge/ChallengeFeedView.vue")
const ChallengeRankingView = () => import("@/views/challenge/ChallengeRankingView.vue")
const MyChallengeView = () => import("@/views/challenge/MyChallengeView.vue")
const MyFeedView = () => import("@/views/challenge/MyFeedView.vue")
const DashboardView = () => import("@/views/dashboard/DashboardView.vue")
const PointShopView = () => import("@/views/product/PointShopView.vue")
const PointHistoryView = () => import("@/views/product/PointHistoryView.vue")
const ReportListView = () => import("@/views/report/ReportListView.vue")
const ReportDetailView = () => import("@/views/report/ReportDetailView.vue")
const SettingsView = () => import("@/views/user/SettingsView.vue")
const ProfileSettingsView = () => import("@/views/user/ProfileSettingsView.vue")
const ConnectionManagementView = () => import("@/views/user/ConnectionManagementView.vue")
const PasswordSettingsView = () => import("@/views/user/PasswordSettingsView.vue")
const ChatView = () => import("@/views/chat/ChatView.vue")

const withAppShell = (route) => ({
  ...route,
  meta: {
    ...route.meta,
    requiresAuth: true,
    appShell: true,
  },
  })

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
    {
      path: "/onboarding",
      name: "onboarding-welcome",
      component: OnboardingWelcomeView,
      meta: { requiresAuth: true },
    },
    // 첫 로그인 사용자의 통합 자산 연결 페이지로 이동하는 주소임
    {
       path: "/connections/mydata",
       name: "connection",
       component: ConnectionView,
       meta: { requiresAuth: true },
    },
    // 로그인 이후 사이드바와 상단바를 공통으로 사용하는 페이지 그룹임
    ...[

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
          path: "/ai-analysis",
          name: "ai-analysis",
          component: AnalysisDashboardView,
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
        {
          path: "/assets/categories",
          name: "category-expenses",
          component: CategoryExpenseView,
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
        // 포인트 내역 페이지로 이동하는 주소임
        {
          path: "/point-history",
          name: "point-history",
          component: PointHistoryView,
        },
        // 금융 리포트 페이지로 이동하는 주소임
        {
          path: "/reports",
          name: "reports",
          component: ReportListView,
        },
        // 금융 리포트 상세 페이지로 이동하는 주소임
        {
          path: "/reports/:newsId",
          name: "report-detail",
          component: ReportDetailView,
        },
        // 사용자 설정 페이지로 이동하는 주소임
        {
          path: "/users/profile",
          component: SettingsView,
          children: [
            {
              path: "",
              name: "user-profile",
              component: ProfileSettingsView,
            },
            {
              path: "connections",
              name: "connection-management",
              component: ConnectionManagementView,
            },
            {
              path: "password",
              name: "password-settings",
              component: PasswordSettingsView,
            },
          ],
        },
    ].map(withAppShell),
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
    to.name !== "connection" &&
    to.name !== "onboarding-welcome"
  ) {
    return { name: "onboarding-welcome", replace: true }
  }

  if (to.meta.guestOnly && userStore.isAuthenticated) {
    return {
      name: userStore.user?.connectionCompleted ? "dashboard" : "onboarding-welcome",
      replace: true,
    }
  }

  return true
})

export default router
