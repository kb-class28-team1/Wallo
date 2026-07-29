import { createRouter, createWebHistory } from "vue-router";
import DefaultLayout from "@/layouts/DefaultLayout.vue";
import ConnectionView from "@/views/asset/ConnectionView.vue";
import DashboardView from "@/views/dashboard/DashboardView.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "Landing",
      component: DashboardView, // TODO: LandingView 추가 후 교체 필요
    },
    {
      path: "/login",
      name: "Login",
      component: DashboardView, // TODO: LoginView 추가 후 교체 필요
    },
    {
      path: "/signup",
      name: "Signup",
      component: DashboardView, // TODO: SignupView 추가 후 교체 필요
    },
    {
      path: "/asset-connections",
      name: "AssetConnection",
      component: ConnectionView,
    },
    {
      path: "/",
      component: DefaultLayout,
      children: [
        {
          path: "dashboard",
          name: "Dashboard",
          component: DashboardView,
        },
        {
          path: "assets",
          name: "AssetManagement",
          component: DashboardView, // TODO: AssetView 추가 후 교체 필요
        },
        {
          path: "assets/expenses",
          name: "ExpenseHistory",
          component: DashboardView, // TODO: ExpenseHistoryView 추가 후 교체 필요
        },
        {
          path: "ai-assistant",
          name: "AiConsulting",
          component: DashboardView, // TODO: AiAssistantView 추가 후 교체 필요
        },
        {
          path: "challenges/current",
          name: "CurrentChallenge",
          component: DashboardView, // TODO: ChallengeEntryView 추가 후 교체 필요
        },
        {
          path: "challenges/:challengeId/feeds",
          name: "ChallengeFeed",
          component: DashboardView, // TODO: ChallengeFeedView 추가 후 교체 필요
        },
        {
          path: "challenges/rankings/weekly",
          name: "WeeklyRanking",
          component: DashboardView, // TODO: ChallengeRankingView 추가 후 교체 필요
        },
        {
          path: "my-challenge",
          name: "MyChallenge",
          component: DashboardView, // TODO: MyChallengeView 추가 후 교체 필요
        },
        {
          path: "point-shop",
          name: "PointShop",
          component: DashboardView, // TODO: PointShopView 추가 후 교체 필요
        },
        {
          path: "reports",
          name: "Reports",
          component: DashboardView, // TODO: ReportListView 추가 후 교체 필요
        },
        {
          path: "settings",
          name: "Settings",
          component: DashboardView, // TODO: SettingsView 추가 후 교체 필요
        },
      ],
    },
  ],
});

export default router;
