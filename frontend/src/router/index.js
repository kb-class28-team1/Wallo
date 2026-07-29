import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import LandingView from '@/views/auth/LandingView.vue'
import LoginView from '@/views/auth/LoginView.vue'
import SignupView from '@/views/auth/SignupView.vue'
import AiAssistantView from '@/views/ai/AiAssistantView.vue'
import AssetView from '@/views/asset/AssetView.vue'
import ConnectionView from '@/views/asset/ConnectionView.vue'
import ChallengeEntryView from '@/views/challenge/ChallengeEntryView.vue'
import ChallengeFeedView from '@/views/challenge/ChallengeFeedView.vue'
import ChallengeRankingView from '@/views/challenge/ChallengeRankingView.vue'
import MyChallengeView from '@/views/challenge/MyChallengeView.vue'
import DashboardView from '@/views/dashboard/DashboardView.vue'
import PointShopView from '@/views/product/PointShopView.vue'
import ReportListView from '@/views/report/ReportListView.vue'
import SettingsView from '@/views/user/SettingsView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 서비스 최초 진입 시 랜딩 페이지를 표시하는 주소임
    {
      path: '/',
      name: 'landing',
      component: LandingView,
    },
    // 로그인 페이지로 이동하는 주소임
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    // 회원가입 페이지로 이동하는 주소임
    {
      path: '/signup',
      name: 'signup',
      component: SignupView,
    },
    // 로그인 이후 사이드바와 상단바를 공통으로 사용하는 페이지 그룹임
    {
      path: '/app',
      component: DefaultLayout,
      children: [
        // 첫 로그인 사용자의 통합 자산 연결 페이지로 이동하는 주소임
        {
          path: '/connections/mydata',
          name: 'connection',
          component: ConnectionView,
        },
        // 대시보드 페이지로 이동하는 주소임
        {
          path: '/home',
          name: 'dashboard',
          component: DashboardView,
        },
        // AI 컨설팅 페이지로 이동하는 주소임
        {
          path: '/dashboard',
          name: 'ai-consulting',
          component: AiAssistantView,
        },
        // 자산 페이지로 이동하는 주소임
        {
          path: '/institutions',
          name: 'institutions',
          component: AssetView,
        },
        // 절약 챌린지의 피드 목록 페이지로 이동하는 주소임
        {
          path: '/challenges/current',
          name: 'current-challenge',
          component: ChallengeEntryView,
        },
        // 선택한 챌린지의 피드 페이지로 이동하는 주소임
        {
          path: '/challenges/:challengeId/feeds',
          name: 'challenge-feed',
          component: ChallengeFeedView,
        },
        // 절약 챌린지 주간랭킹 페이지로 이동하는 주소임
        {
          path: '/challenges/rankings/weekly',
          name: 'weekly-ranking',
          component: ChallengeRankingView,
        },
        // 현재 사용자의 내 챌린지 페이지로 이동하는 주소임
        {
          path: '/users/me/challenge-dashboard',
          name: 'my-challenge',
          component: MyChallengeView,
        },
        // 포인트 샵 페이지로 이동하는 주소임
        {
          path: '/point-shop',
          name: 'point-shop',
          component: PointShopView,
        },
        // 금융 리포트 페이지로 이동하는 주소임
        {
          path: '/reports',
          name: 'reports',
          component: ReportListView,
        },
        // 사용자 설정 페이지로 이동하는 주소임
        {
          path: '/users/profile',
          name: 'user-profile',
          component: SettingsView,
        },
      ],
    },
  ],
})

export default router
