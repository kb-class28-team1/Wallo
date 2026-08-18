# Wallo 프로젝트 기능 분석서

> 분석 기준: README가 아니라 `frontend/src`, `backend/src/main`, `ai/app`, MyBatis XML을 서로 대조했다.  
> 확인 일자: 2026-08-13  
> 주의: 저장소에 전체 운영 DB 생성문이 없으므로, 일부 테이블의 정확한 열·제약조건은 **코드상 확인되지 않음**으로 표시한다.
>
> 소비분석·금융리포트의 폴더 및 파일별 상세 설명은 `AGENT_FILE_GUIDE.md`를 함께 참고한다.

## 먼저 확인한 프로젝트 구조와 실행 방법

### 구조

| 영역 | 역할 | 핵심 경로 |
|---|---|---|
| `frontend` | 사용자가 보는 웹 화면 | `frontend/src/views`, `frontend/src/components`, `frontend/src/stores`, `frontend/src/api` |
| `backend` | 로그인, 자산, 목표, 챌린지, 포인트, 리포트 등의 업무 처리와 DB 저장 | `backend/src/main/java/com/wallo`, `backend/src/main/resources/mapper` |
| `ai` | 대화, 목표 인터뷰, 소비 분석, 거래 분류, 뉴스 리포트 문장 생성 | `ai/app`, `ai/app/application.py` |
| `database` | 금융 뉴스·용어·소비 분석 일부 테이블 생성문과 시드 | `database/sql`, `database/seed` |

전체 구성은 **Vue 화면 → Spring 서버 → MySQL**이 기본 흐름이고, AI가 필요한 작업은 **Spring 서버 → FastAPI AI 서버 → Groq**로 한 번 더 이어진다. 외부 금융 데이터는 현재 기본 설정상 실제 금융기관 대신 Spring 서버 안의 CODEF 목업 API를 호출한다.

관련 파일: `frontend/vite.config.js`, `backend/build.gradle`, `backend/src/main/webapp/WEB-INF/web.xml`, `backend/src/main/java/com/wallo/config/AppConfig.java`, `ai/app/application.py`

### 로컬 실행 순서

1. MySQL에 `wallo` DB와 프로젝트가 요구하는 테이블을 준비한다. 금융 리포트 기능은 `database/sql/README.md` 순서로 테이블을 만들고 `ai/data/processed/financial_term_insert.sql`로 금융용어를 적재한다.
2. AI 서버: `cd ai` → 가상환경 생성 → `pip install -r requirements.txt` → `.env` 작성 → `uvicorn app.application:app --reload --port 8000`.
3. 백엔드: Java 17과 외부 Tomcat 9 환경에서 `backend`를 WAR로 실행한다. 수동 빌드는 Windows 기준 `backend\gradlew.bat build`, 산출물은 `backend/build/libs/ROOT.war`이다.
4. 프론트엔드: `cd frontend` → `npm install`(또는 저장소 실행 가이드의 `pnpm install`) → `npm run dev`. 기본 주소는 `http://localhost:5173`, `/api`와 `/ws`는 `localhost:8080`으로 전달된다.

관련 파일: `RUNBOOK.md`, `frontend/package.json`, `frontend/vite.config.js`, `backend/build.gradle`, `backend/src/main/resources/application.properties`, `ai/.env.example`, `ai/requirements.txt`

---

## 1. 프로젝트 한 줄 소개

**Wallo는 흩어진 금융자산과 소비를 한곳에서 보고, AI 상담으로 저축 목표를 세우며, 절약 챌린지와 보상 기능으로 실행을 돕는 개인 금융 관리 서비스다.**

관련 파일: `frontend/src/router/index.js`, `backend/src/main/java/com/wallo`, `ai/app/application.py`

## 2. 사용자 관점의 핵심 기능 목록

### 2.1 회원가입·로그인

- **무엇을 할 수 있나:** 이름, 닉네임, 이메일, 비밀번호로 가입하고 로그인·로그아웃할 수 있다. 새로고침 후에도 서버 세션으로 로그인을 복구한다.
- **관련 화면/API:** `/signup`, `/login`; `POST /api/auth/signup`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`.
- **처리 흐름:** 화면 입력 검증 → 이메일 소문자 정리 → 이메일·닉네임 중복 검사 → 비밀번호 BCrypt 암호화 저장 → 로그인 성공 시 서버 세션에 사용자 ID 저장 → 연결 자산 유무에 따라 자산 연결 또는 대시보드로 이동.
- **관련 파일:** `frontend/src/views/auth/SignupView.vue`, `frontend/src/views/auth/LoginView.vue`, `frontend/src/stores/userStore.js`, `backend/src/main/java/com/wallo/auth/controller/AuthController.java`, `backend/src/main/java/com/wallo/auth/service/AuthServiceImpl.java`, `backend/src/main/resources/mapper/auth/AuthMapper.xml`

### 2.2 최초 자산 연결과 동기화

- **무엇을 할 수 있나:** 본인 이름·전화번호·수집 동의를 입력해 은행·카드·증권 자산을 한꺼번에 연결하고, 이후 최신 거래를 다시 동기화할 수 있다.
- **관련 화면/API:** `/connections/mydata`, `/assets`, `/assets/expenses`; `POST /api/connections`, `POST /api/assets/sync`, `GET /api/connections`, `DELETE /api/connections/{connectionId}`.
- **처리 흐름:** 동의 확인 → 지원 기관 목록 순회 → 계좌·카드·증권 및 거래 조회 → 계좌/카드/거래를 중복 없이 저장 → 카드 출금과 계좌 출금을 맞춰 중복 지출을 조정 → 자산 스냅샷·연봉 자료 갱신 → 결과를 기관별 성공/실패로 표시.
- **현재 실제 데이터 원천:** 기본값은 CODEF 목업 JSON이다. 실제 기관 연결은 설정 구조만 있고 운영 연결 여부는 코드상 확인되지 않음.
- **관련 파일:** `frontend/src/views/asset/ConnectionView.vue`, `frontend/src/views/user/ConnectionManagementView.vue`, `backend/src/main/java/com/wallo/asset/service/ConnectionService.java`, `backend/src/main/java/com/wallo/asset/service/AssetSyncOrchestrator.java`, `backend/src/main/java/com/wallo/asset/service/AssetSyncWorker.java`, `backend/src/main/java/com/wallo/asset/service/CardWithdrawalReconciliationService.java`, `backend/src/main/resources/mapper/asset/AssetSyncMapper.xml`, `backend/src/main/resources/mock/codef`

### 2.3 대시보드

- **무엇을 할 수 있나:** 총자산, 월 예산, 월 지출, 카테고리별 소비, 자산 변화, 확정 목표를 한 화면에서 본다. 예산 설정과 상세 자산/소비 화면으로 이동한다.
- **관련 화면/API:** `/dashboard`; 자산·지출·예산·목표 조회 API 묶음.
- **처리 흐름:** Pinia 대시보드 저장소가 자산·지출·예산을 병렬 조회 → 차트 데이터 계산 → 목표 저장소에서 목표와 로드맵 조회 → 카드별 상태 표시. 화면이 다시 활성화되거나 일정 시간이 지나면 목표를 갱신한다.
- **관련 파일:** `frontend/src/views/dashboard/DashboardView.vue`, `frontend/src/stores/useDashboardStore.js`, `frontend/src/features/financial/useDashboardCharts.js`, `frontend/src/components/dashboard`, `backend/src/main/java/com/wallo/asset/controller/AssetController.java`, `backend/src/main/java/com/wallo/asset/controller/BudgetController.java`, `backend/src/main/java/com/wallo/goal/controller/GoalController.java`

### 2.4 자산·소비·예산·연말정산 정보

- **무엇을 할 수 있나:** 보유 계좌/카드/증권과 합계, 월별 거래 달력·목록·카테고리 비중, 총예산과 카테고리 예산, AI 소비 코멘트, 연봉 기반 연말정산 공제 진행률을 볼 수 있다.
- **관련 화면/API:** `/assets`, `/assets/expenses`; `GET /api/assets`, `GET /api/assets/expense`, `GET/PUT /api/budgets`, `GET/PUT /api/budgets/categories`, `GET /api/asset-reports/insights/`, `GET /api/asset-reports/tax-settlement`, `PATCH /api/users/profile`(연봉).
- **처리 흐름:** 선택 월의 거래를 페이지 단위 조회 → 날짜·카테고리·합계로 집계 → 현재 월이면 카테고리 예산 편집 → 거래와 예산을 AI 서버에 보내 소비 인사이트 생성 → 연봉과 공제 대상 소비를 조합해 공제 현황 계산.
- **관련 파일:** `frontend/src/views/asset/AssetView.vue`, `frontend/src/views/asset/ExpenseHistoryView.vue`, `frontend/src/components/asset`, `frontend/src/stores/assetStore.js`, `frontend/src/stores/budgetStore.js`, `backend/src/main/java/com/wallo/asset/service/AssetService.java`, `ExpenseService.java`, `BudgetService.java`, `AssetReportService.java`, `backend/src/main/resources/mapper/asset`

### 2.5 AI 금융 상담과 목표 설정

- **무엇을 할 수 있나:** 여러 상담방을 만들고 AI와 대화하며, 상담방 제목 변경·삭제, 소비 분석 질문, 목표 설정 인터뷰, 목표 계좌 선택, 목표 로드맵 단계 완료 처리를 할 수 있다.
- **관련 화면/API:** `/chat`, `/ai-consulting`; 대화방 API `GET/POST/PATCH/DELETE /api/conversations...`, 메시지 API `POST /api/conversations/{id}/messages`, 목표 API `/api/goals...`.
- **처리 흐름:** 사용자가 메시지 입력 → Spring이 사용자·자산·소비 문맥과 기존 대화를 구성 → FastAPI `/api/chat` 호출 → 일반 답변/소비 분석/목표 인터뷰 결과를 DB에 저장 → 목표 인터뷰가 완성되면 목표와 3단계 로드맵 저장 → 사용자가 사용할 계좌를 연결하고 진행 단계를 체크.
- **관련 파일:** `frontend/src/views/chat/ChatView.vue`, `frontend/src/views/ai/AiAssistantView.vue`, `frontend/src/stores/conversationStore.js`, `frontend/src/stores/goalStore.js`, `backend/src/main/java/com/wallo/chat`, `backend/src/main/java/com/wallo/goal`, `backend/src/main/resources/mapper/chat`, `backend/src/main/resources/mapper/goal`, `ai/app/chat`, `ai/app/agents/goal`, `ai/app/agents/roadmap`

### 2.6 절약 챌린지·피드·그룹 채팅

- **무엇을 할 수 있나:** 개인/그룹 챌린지를 만들거나 초대 코드로 참여하고, 절약 인증 글과 사진을 올리고, AI 분석 결과를 확인하며, 좋아요·수정·삭제, 그룹 메시지, 챌린지 탈퇴를 할 수 있다.
- **관련 화면/API:** `/challenges/current`, `/challenges/{challengeId}/feeds`; 챌린지 API `/api/challenges`, 피드 API `/api/challenges/{id}/feeds...`, 메시지 API `/api/challenges/{id}/messages`.
- **처리 흐름:** 챌린지 생성/참여 → 피드 이미지와 내용을 먼저 분석 → 사용자가 분석 피드백·절약액과 함께 게시 → 피드·분석·피드백 저장 → 참여자 화면과 메시지에 반영 → 좋아요 수와 누적 절약액 갱신.
- **관련 파일:** `frontend/src/views/challenge/ChallengeEntryView.vue`, `ChallengeFeedView.vue`, `frontend/src/api/challengeApi.js`, `frontend/src/api/feedApi.js`, `backend/src/main/java/com/wallo/challenge`, `backend/src/main/java/com/wallo/feed`, `backend/src/main/resources/mapper/challenge`, `backend/src/main/resources/mapper/feed`

### 2.7 주간 랭킹·내 챌린지·내 게시물

- **무엇을 할 수 있나:** 그룹 내 이번 주 절약 순위, 내 순위와 보상 상태를 보고, 기간별 절약 추이·인기 글·작성 글을 조회한다.
- **관련 화면/API:** `/challenges/rankings/weekly`, `/users/me/challenge-dashboard`, `/my-feeds`; `GET /api/challenges/rankings/weekly`, `GET /api/users/me/challenge-dashboard`, `GET /api/users/me/feeds`.
- **처리 흐름:** 피드의 절약 금액을 주 단위로 합산 → 순위 계산 → 보상 지급 기록과 사용자 포인트 반영 → 내 화면은 선택 기간(예: 6개월)의 추이와 인기 피드를 집계.
- **관련 파일:** `frontend/src/views/challenge/ChallengeRankingView.vue`, `MyChallengeView.vue`, `MyFeedView.vue`, `backend/src/main/java/com/wallo/challenge/service/ChallengeServiceImpl.java`, `WeeklyRankingRewardServiceImpl.java`, `MyFeedServiceImpl.java`, `backend/src/main/resources/mapper/challenge`

### 2.8 포인트샵·보관함·포인트 내역

- **무엇을 할 수 있나:** 보유 포인트로 랜덤박스를 1개 또는 10개 열고, 포인트/아이템 결과를 받으며, 보관함과 포인트 증감 내역을 확인하고 사용 완료 아이템을 지울 수 있다.
- **관련 화면/API:** `/point-shop`, `/point-history`; `GET /api/point-shop`, `GET /api/point-shop/boxes/{id}`, `POST .../open`, `POST .../open-bulk`, `DELETE /api/users/me/inventory/{id}`, `GET /api/point-history`.
- **처리 흐름:** 잔액·박스·보관함 조회 → 구매 가능 여부 확인 → 포인트 차감 → 서버 확률표로 보상 추첨 → 포인트 이력 또는 보관함 저장 → 화면 잔액과 결과 모달 갱신.
- **관련 파일:** `frontend/src/views/product/PointShopView.vue`, `PointHistoryView.vue`, `frontend/src/api/pointShopApi.js`, `pointHistoryApi.js`, `backend/src/main/java/com/wallo/pointshop`, `backend/src/main/resources/mapper/pointshop`

### 2.9 금융 뉴스 리포트와 금융용어 설명

- **무엇을 할 수 있나:** 최근 뉴스의 AI 요약·쉬운 해석·영향·행동 제안을 보고, 기사 속 금융용어를 눌러 뜻을 확인한다. 읽은 리포트는 브라우저에서 표시된다.
- **관련 화면/API:** `/reports`, `/reports/{newsId}`; `GET /api/reports`, `GET /api/reports/{id}`, 관리/시연용 `POST /crawl-now`, `POST /generate-missing`, `POST /{id}/generate`.
- **처리 흐름:** Selenium 뉴스 수집 → 뉴스 DB 저장 → FastAPI가 리포트 생성 → 금융용어 사전과 제목/본문 대조 → 리포트와 뉴스-용어 연결 저장 → 상세 화면에서 용어 강조와 설명 패널 표시.
- **관련 파일:** `frontend/src/views/report/ReportListView.vue`, `ReportDetailView.vue`, `frontend/src/components/report`, `backend/src/main/java/com/wallo/report`, `backend/src/main/resources/mapper/report`, `ai/app/reports`, `database/sql/news*.sql`, `database/sql/financial_term.sql`

### 2.10 사용자 설정

- **무엇을 할 수 있나:** 닉네임, 프로필 사진, 비밀번호를 바꾸고 연결된 자산을 종류별로 확인·해제한다.
- **관련 화면/API:** `/users/profile`, `/users/profile/password`, `/users/profile/connections`; `GET/PATCH/DELETE /api/users/profile...`, `GET/DELETE /api/connections...`.
- **처리 흐름:** 현재 프로필 조회 → 입력 검증 → 사용자 행 수정 또는 이미지 파일 저장 → Pinia의 상단 프로필 즉시 갱신. 연결 해제는 확인창 후 연결을 비활성화하고 자산 화면 캐시를 비운다.
- **관련 파일:** `frontend/src/views/user`, `frontend/src/stores/userStore.js`, `backend/src/main/java/com/wallo/user`, `backend/src/main/resources/mapper/user/UserMapper.xml`

## 3. 화면별 기능 정리

| 화면 | 진입 방법 | 가능한 행동 | 주요 상태 변화 | 관련 파일 |
|---|---|---|---|---|
| 랜딩 | `/` | 서비스 소개 확인, 로그인 이동 | 화면 내 로딩 애니메이션만 변화 | `frontend/src/views/auth/LandingView.vue` |
| 회원가입 | `/signup` | 정보 입력, 가입 | 오류 표시 → 성공 모달 → 로그인 이동 | `frontend/src/views/auth/SignupView.vue` |
| 로그인 | `/login` | 로그인, 회원가입 이동 | 사용자 Pinia 저장, 세션 만료 안내, 연결 여부별 이동 | `frontend/src/views/auth/LoginView.vue` |
| 최초 자산 연결 | 로그인 후 연결이 없으면 자동 진입 또는 `/connections/mydata` | 이름·전화번호·동의 입력, 전체 연결 | 진행률/기관별 성공·실패 표시, 연결 완료 상태로 변경 | `frontend/src/views/asset/ConnectionView.vue` |
| 대시보드 | 사이드바 홈, `/dashboard` | 자산·예산·지출·목표 요약 확인, 상세/예산 설정 이동 | 조회 중/실패/빈 상태/데이터 상태, 차트 갱신 | `frontend/src/views/dashboard/DashboardView.vue` |
| 자산 | 사이드바 자산, `/assets` | 자산 목록·AI 소비 리포트·공제 현황 확인, 동기화 | 자산/거래/인사이트 재조회, 동기화 결과 표시 | `frontend/src/views/asset/AssetView.vue` |
| 소비 내역 | `/assets/expenses` 또는 대시보드 상세 | 월 이동, 달력/목록 전환, 날짜 상세, 더 보기, 예산 편집, 동기화 | 선택 월·날짜·페이지·예산·합계 변경 | `frontend/src/views/asset/ExpenseHistoryView.vue` |
| AI 컨설팅 요약 | 사이드바, `/ai-consulting` | 대표 목표 확인, 로드맵 단계 체크, 목표 설정 시작 | 목표 금액/달성률/남은 기간/로드맵 완료 변경 | `frontend/src/views/ai/AiAssistantView.vue` |
| AI 채팅 | `/chat` 또는 목표 설정 버튼 | 대화방 생성·선택·제목 변경·삭제, 메시지 전송, 목표 인터뷰 응답, 계좌 선택 | 메시지, 인터뷰 단계, 확정 목표, 선택 계좌 변경 | `frontend/src/views/chat/ChatView.vue` |
| 챌린지 입장 | 사이드바 절약 챌린지 또는 `/challenges/current` | 개인/그룹 챌린지 생성, 초대 코드 참여 | 참여 전 → 생성/참여 완료 → 피드 이동 | `frontend/src/views/challenge/ChallengeEntryView.vue` |
| 챌린지 피드 | 챌린지 메뉴 또는 `/challenges/{id}/feeds` | 목록/내 글 필터, 글 작성·AI 분석·수정·삭제·좋아요, 그룹 채팅, 탈퇴 | 피드·좋아요·절약액·채팅·멤버십 변경 | `frontend/src/views/challenge/ChallengeFeedView.vue` |
| 주간 랭킹 | 챌린지 하위 메뉴, `/challenges/rankings/weekly` | 순위·보상 확인, 개발용 즉시 보상 버튼 | 순위 및 사용자 포인트 갱신 | `frontend/src/views/challenge/ChallengeRankingView.vue` |
| 내 챌린지 | 챌린지 하위 메뉴, `/users/me/challenge-dashboard` | 기간 선택, 통계·추이·인기 글 확인 | 기간별 집계와 차트 변경 | `frontend/src/views/challenge/MyChallengeView.vue` |
| 내 게시물 | `/my-feeds` | 정렬·카테고리 필터·페이지 이동, 원 챌린지 피드 이동 | 필터/페이지/목록 상태 변경 | `frontend/src/views/challenge/MyFeedView.vue` |
| 포인트샵 | 상단 포인트 또는 `/point-shop` | 박스 1회/10회 열기, 보관함 확인, 사용 완료 삭제 | 포인트 차감, 결과 모달, 보관함 갱신 | `frontend/src/views/product/PointShopView.vue` |
| 포인트 내역 | `/point-history` | 기간/종류별 포인트 내역과 요약 확인 | 필터·페이지·잔액/적립/사용 합계 변경 | `frontend/src/views/product/PointHistoryView.vue` |
| 리포트 목록 | 사이드바 금융 리포트, `/reports` | 목록·읽음 상태 확인, 시연용 뉴스 수집/미생성 리포트 생성 | 수집/생성 진행, 목록과 로컬 읽음 상태 변경 | `frontend/src/views/report/ReportListView.vue` |
| 리포트 상세 | 목록 카드, `/reports/{newsId}` | 분석 섹션 읽기, 용어 클릭·설명 확인, 원문 이동 | 읽음 ID를 `localStorage`에 저장, 선택 용어 변경 | `frontend/src/views/report/ReportDetailView.vue` |
| 설정 틀 | 상단 프로필, `/users/profile` | 프로필/자산 연결/비밀번호 탭 이동 | 하위 설정 화면 전환 | `frontend/src/views/user/SettingsView.vue` |
| 프로필 설정 | `/users/profile` | 닉네임 저장·취소, JPG/PNG 사진 변경, 기본 사진 복원 | 사용자 정보와 미리보기 변경 | `frontend/src/views/user/ProfileSettingsView.vue` |
| 연결 관리 | `/users/profile/connections` | 계좌/카드/증권별 보기, 연결 해제 | 연결 수·합계·활성 연결 변경 | `frontend/src/views/user/ConnectionManagementView.vue` |
| 비밀번호 설정 | `/users/profile/password` | 현재/새 비밀번호 입력 및 변경 | 검증 오류 또는 성공 상태 | `frontend/src/views/user/PasswordSettingsView.vue` |

공통 앱 화면의 사이드바에는 홈, AI 컨설팅, 자산, 챌린지 하위 메뉴, 포인트샵, 금융 리포트 등이 있고, 상단에는 오늘의 미션·프로필·포인트·로그아웃이 있다. 관련 파일: `frontend/src/components/navigation/SideNavigation.vue`, `TopHeader.vue`, `frontend/src/App.vue`.

## 4. 백엔드 기능 정리

### 4.1 인증·사용자

| API | 역할 |
|---|---|
| `POST /api/auth/signup` | 입력 검증, 이메일/닉네임 중복 검사, BCrypt 비밀번호 저장 |
| `POST /api/auth/login` | 이메일/비밀번호 확인, 기존 세션 폐기 후 새 로그인 세션 생성 |
| `GET /api/auth/me` | 세션 사용자 조회와 활성 자산 연결 여부 반환 |
| `POST /api/auth/logout` | 세션 폐기 |
| `GET /api/users/profile` | 프로필·연봉·포인트 등 조회 |
| `PATCH /api/users/profile/nickname` | 닉네임 검증·중복 검사 후 변경 |
| `PATCH /api/users/profile/password` | 현재 비밀번호 확인 후 새 비밀번호 암호화 저장 |
| `PATCH/DELETE /api/users/profile/image` | 이미지 검증·파일 저장 또는 기본 이미지 복원 |
| `GET /api/profile-images/{filename}` | 저장된 프로필 이미지 제공 |

- **DB:** `USERS`가 중심이며 `CONNECTIONS`, `POINT_HISTORY`, `USER_INVENTORY`, 챌린지·피드·대화 데이터가 사용자 ID로 이어진다. 전체 외래키 정의는 코드상 확인되지 않음.
- **관련 파일:** `backend/src/main/java/com/wallo/auth`, `backend/src/main/java/com/wallo/user`, `backend/src/main/resources/mapper/auth/AuthMapper.xml`, `backend/src/main/resources/mapper/user/UserMapper.xml`

### 4.2 자산·거래·예산

| API | 역할 |
|---|---|
| `GET /api/institutions` | 연결 가능한 기관 목록 |
| `GET/POST /api/connections` | 내 연결 조회 / 동의 후 전체 기관 연결 |
| `DELETE /api/connections/{id}` | 소유권 확인 후 연결 해제 |
| `GET /api/assets` | 총자산·기관/상품별 자산·자산 추이 조회 |
| `POST /api/assets/sync` | 활성 연결의 계좌·카드·증권·거래 재수집 |
| `GET /api/assets/expense` | 기간·날짜·페이지 조건의 지출, 합계, 카테고리 집계 |
| `GET/PUT /api/budgets` | 월 총예산 조회·저장 |
| `GET/PUT /api/budgets/categories` | 카테고리별 예산과 실제 지출 조회·일괄 저장 |
| `GET /api/asset-reports/insights/` | 소비 분석 조회/생성 |
| `GET /api/asset-reports/tax-settlement` | 연말정산 공제 추정값 조회 |
| `PATCH /api/users/profile` | 연봉 저장 |

- **주요 로직:** 기관 연결, 계좌/카드 번호 정규화, 배치 중복 제거, 원천 거래 식별키 생성, 카드 승인과 계좌 출금 중복 조정, 상호 관계 검증, 지출 카테고리 규칙 및 AI 분류, 목표용 계좌 분류, 월별 자산 스냅샷 저장.
- **DB 관계:** `INSTITUTIONS → CONNECTIONS → ACCOUNTS/CARDS → TRANSACTIONS`; `USERS → ASSET_SNAPSHOTS`; `USERS → BUDGETS` 또는 `BUDGET_PLANS → BUDGET_PLAN_CATEGORIES`. 거래는 계좌 또는 카드와 연결되며 정확한 DB 제약은 전체 생성문 부재로 코드상 확인되지 않음.
- **관련 파일:** `backend/src/main/java/com/wallo/asset`, `backend/src/main/resources/mapper/asset`, `backend/src/main/java/com/wallo/external`, `backend/src/main/resources/mock/codef`

### 4.3 대화·목표·소비 분석

| API | 역할 |
|---|---|
| `GET/POST /api/conversations` | 내 대화방 목록·생성 |
| `GET /api/conversations/{id}/messages` | 메시지 이력 조회 |
| `POST /api/conversations/{id}/messages` | 메시지 저장, AI 호출, AI 답변/분석/목표 처리 |
| `GET /api/conversations/{id}/goal-interview` | 진행 중 목표 인터뷰 복원 |
| `PATCH/DELETE /api/conversations/{id}` | 대화방 제목 변경·소프트 삭제 |
| `GET /api/goals` | 확정 목표 목록 |
| `GET /api/conversations/{id}/goal` | 대화방에서 만든 목표 조회 |
| `GET /api/goals/available-accounts` | 목표에 배정 가능한 계좌 목록 |
| `PUT /api/goals/{id}/account` | 목표 계좌 교체 |
| `GET /api/goals/{id}/roadmap` | 3단계 로드맵 조회 |
| `PUT /api/goals/{id}/roadmap/steps/{n}` | 로드맵 완료 여부 변경 |
| `POST /api/chat` | 별도 단발 AI 채팅 엔드포인트 |

- **주요 로직:** 대화 소유권 검사, 메시지 영속화, 긴 대화 요약, 소비 분석용 거래·예산 문맥 구성 및 결과 캐시, 목표 항목 추출·검증·확정, 목표 가능성 계산, 로드맵 생성, 목표 계좌 중복 배정 방지.
- **DB 관계:** `USERS → CONVERSATIONS → CHAT_MESSAGES`; 대화/사용자와 `CONSUMPTION_ANALYSIS_RESULTS`; `CONVERSATIONS → GOAL_INTERVIEW_SESSIONS → FINANCIAL_GOALS → GOAL_ROADMAPS`; 목표와 계좌의 연결 테이블 `FINANCIAL_GOAL_ACCOUNTS`.
- **관련 파일:** `backend/src/main/java/com/wallo/chat`, `backend/src/main/java/com/wallo/goal`, `backend/src/main/java/com/wallo/asset/service/ConsumptionAnalysisContextService.java`, `backend/src/main/resources/mapper/chat`, `backend/src/main/resources/mapper/goal`, `ai/app/chat`, `ai/app/agents`

### 4.4 챌린지·피드·랭킹

| API | 역할 |
|---|---|
| `POST /api/challenges` | 개인/그룹 챌린지 생성, 그룹 초대 코드 생성 |
| `POST /api/challenges/join` | 초대 코드로 참여 |
| `DELETE /api/challenges/{id}/membership` | 챌린지 탈퇴 |
| `GET /api/challenges/current` | 현재 참여 챌린지 |
| `GET /api/challenges/rankings/weekly` | 주간 순위와 보상 상태 |
| `POST /api/challenges/rankings/weekly/reward/test` | 현재 주 보상을 즉시 지급하는 개발용 API |
| `GET /api/users/me/challenge-dashboard` | 내 절약 통계·추이·인기 피드 |
| `GET /api/users/me/feeds` | 내 게시물 필터/페이지 조회 |
| `GET/POST/PATCH/DELETE /api/challenges/{id}/feeds...` | 피드 목록·작성·수정·삭제 |
| `POST /api/challenges/{id}/feeds/analyze` | 이미지/글 사전 분석 |
| `POST /api/challenges/{id}/feeds/{feedId}/like` | 좋아요 수 증가 |
| `GET/POST /api/challenges/{id}/messages` | 그룹 메시지 조회·전송 |
| `GET /api/feed-media/{filename}` | 피드 이미지 제공 |

- **주요 로직:** 참여 여부·작성자 권한 검사, 피드 카테고리 및 절약액 저장, AI 분석·사용자 피드백 저장, 음식이면 레시피 재료비/외식비/쇼핑가 참조 정보 계산, 주간 순위 집계와 중복 보상 방지, 포인트 지급.
- **DB 관계:** `USERS ↔ CHALLENGE`; `CHALLENGE → FEED → FEED_ANALYSIS/FEED_ANALYSIS_FEEDBACK`; `CHALLENGE → MESSAGE`; 피드와 가격 참조 테이블들; 보상은 `USERS.point`와 `POINT_HISTORY`에 반영되는 구조다. 멤버십 테이블명과 전체 제약은 코드상 확인되지 않음.
- **관련 파일:** `backend/src/main/java/com/wallo/challenge`, `backend/src/main/java/com/wallo/feed`, `backend/src/main/resources/mapper/challenge`, `feed`

### 4.5 포인트샵

| API | 역할 |
|---|---|
| `GET /api/point-shop` | 잔액·박스 목록·보관함 |
| `GET /api/point-shop/boxes/{id}` | 박스 상세 |
| `POST /api/point-shop/boxes/{id}/open` | 1회 구매/추첨 |
| `POST /api/point-shop/boxes/{id}/open-bulk` | 10회 구매/추첨 |
| `DELETE /api/users/me/inventory/{id}` | 사용 완료 보관함 항목 삭제 |
| `GET /api/point-history` | 기간·유형·페이지별 포인트 이력 |

- **주요 로직:** 잔액 검증, 차감과 이력 기록, 서버 측 보상 추첨, 포인트 보상 즉시 반영, 아이템 보상 보관함 저장, 사용 완료 항목만 삭제.
- **DB 관계:** `USERS → POINT_HISTORY`, `USERS → USER_INVENTORY`. 박스/보상 마스터가 DB인지 코드 상수인지 정확한 출처는 해당 서비스 구현에 혼합되어 있으며 운영 관리 화면은 코드상 확인되지 않음.
- **관련 파일:** `backend/src/main/java/com/wallo/pointshop`, `backend/src/main/resources/mapper/pointshop`

### 4.6 금융 리포트

| API | 역할 |
|---|---|
| `GET /api/reports` | 리포트가 생성된 최근 뉴스 목록 |
| `GET /api/reports/{newsId}` | 뉴스·AI 분석·매칭 용어 상세 |
| `POST /api/reports/{newsId}/generate` | 한 기사 리포트 재생성/용어 재매칭 |
| `POST /api/reports/crawl-now` | 즉시 크롤링과 생성 시작 |
| `POST /api/reports/generate-missing` | 리포트 없는 뉴스 일괄 생성 시작 |
| `/api/test/crawl...` | 크롤러 점검용 공개 테스트 엔드포인트 |

- **주요 로직:** 정해진 시간 뉴스 크롤링, URL 중복 방지, AI 리포트 생성, 금융용어 캐시 매칭, 오래된 뉴스 삭제, 미생성 기사 백그라운드 처리.
- **DB 관계:** `NEWS 1:1 NEWS_REPORT`, `NEWS N:M FINANCIAL_TERM`을 `NEWS_TERM`이 연결한다.
- **관련 파일:** `backend/src/main/java/com/wallo/report`, `backend/src/main/resources/mapper/report`, `database/sql`, `ai/app/reports`

### 4.7 FastAPI AI 서버

| API | Spring에서 쓰는 목적 |
|---|---|
| `GET /api/health` | AI 서버 상태 확인 |
| `POST /api/chat` | 일반 상담, 소비 코칭, 목표 인터뷰/로드맵/상품 추천 도구 실행 |
| `POST /api/chat/summarize` | 긴 대화 요약 |
| `POST /api/category/classify`, `/classify/batch` | 거래 카테고리 분류 |
| `POST /api/asset-reports/insights/generate` | 소비 인사이트 생성 |
| `POST /api/reports/generate` | 뉴스 리포트 문장 생성 |
| `GET /api/demo/asset-profiles`, `POST /api/demo/asset-analysis` | 데모 데이터용 API; 프론트 실제 연결은 코드상 확인되지 않음 |

관련 파일: `ai/app/application.py`, `ai/app/chat`, `ai/app/category`, `ai/app/asset_reports`, `ai/app/reports`, `ai/app/demo`

## 5. 프론트엔드와 백엔드 연결 흐름

### 일반 화면

1. Vue 화면이 Pinia 저장소 또는 `frontend/src/api/*.js` 함수를 호출한다.
2. 공통 Axios가 쿠키를 포함해 `/api/...`로 요청한다.
3. Vite 개발 서버가 요청을 `localhost:8080`의 Tomcat으로 전달한다.
4. Spring Controller가 세션 사용자 ID를 확인하고 Service를 호출한다.
5. Service가 업무 규칙을 적용하고 MyBatis Mapper XML의 SQL로 MySQL을 조회/수정한다.
6. 응답을 Pinia에 저장하고 화면의 로딩·성공·오류·빈 상태를 갱신한다.

관련 파일: `frontend/src/api/httpClient.js`, `frontend/vite.config.js`, `backend/src/main/java/com/wallo/auth/SessionCurrentUserProvider.java`, `backend/src/main/resources/mapper`

### AI가 필요한 화면

1. Vue → Spring까지는 위와 같다.
2. Spring이 자산·거래·예산·대화 같은 사용자 문맥을 조립한다.
3. Spring의 Python 클라이언트가 `127.0.0.1:8000` FastAPI를 호출한다.
4. FastAPI가 규칙 기반 판단과 Groq 호출을 조합해 결과를 반환한다.
5. Spring이 결과를 검증·정규화하고 필요한 경우 DB에 저장한 뒤 Vue로 보낸다.

관련 파일: `backend/src/main/java/com/wallo/chat/client/PythonAiClient.java`, `backend/src/main/java/com/wallo/asset/client/PythonAssetReportAiClient.java`, `backend/src/main/java/com/wallo/asset/classification/PythonCategoryClient.java`, `backend/src/main/java/com/wallo/report/client/PythonNewsReportAiClient.java`, `ai/app`

### 실시간성

- 챌린지 그룹 채팅은 최초 메시지 목록을 REST로 읽은 뒤, `/ws/challenges/{challengeId}` WebSocket으로 새 메시지를 실시간 수신·전송한다.
- 연결 시 서버 세션 로그인과 챌린지 참여 여부를 검사하며, 끊기면 프론트가 최대 10초 간격으로 재연결한다.
- 관련 파일: `frontend/vite.config.js`, `frontend/src/views/challenge/ChallengeFeedView.vue`, `backend/src/main/java/com/wallo/config/WebMvcConfig.java`, `backend/src/main/java/com/wallo/feed/websocket/ChallengeChatWebSocketHandler.java`, `ChallengeChatBroadcaster.java`.

## 6. 로그인·권한·예외 처리 등 공통 기능

### 로그인과 권한

- 인증 방식은 JWT가 아니라 **서버 HttpSession 쿠키**다. 로그인 때 `LOGIN_USER_ID`를 저장하고 API마다 `CurrentUserProvider`로 읽는다.
- 프론트 라우터는 첫 이동 때 `/api/auth/me`로 로그인 상태를 복구한다.
- 보호 화면은 미로그인 시 `/login?redirect=...`로 이동한다.
- 로그인했지만 활성 자산 연결이 없으면 최초 연결 화면으로 강제 이동한다.
- 로그인 사용자가 로그인·회원가입 화면으로 가면 대시보드 또는 연결 화면으로 돌려보낸다.
- 대화·피드·연결·목표 등은 서비스 계층에서 사용자 소유권/참여 여부를 확인하는 코드가 있다. 역할별 관리자 권한 체계는 코드상 확인되지 않음.

관련 파일: `frontend/src/router/index.js`, `frontend/src/main.js`, `frontend/src/stores/userStore.js`, `backend/src/main/java/com/wallo/auth/controller/AuthController.java`, `SessionCurrentUserProvider.java`

### 예외와 사용자 안내

- 공통 Axios는 일반 API에서 401을 받으면 사용자 상태를 지우고 `reason=expired`를 붙여 로그인 화면으로 보낸다.
- 프론트 API 계층은 서버 메시지를 우선 사용하고 없으면 한국어 기본 문구를 만든다. 화면은 경고창, 모달, 화면 내 오류/재시도 상태를 사용한다.
- Spring 공통 예외 처리기는 인증 실패 401, 잘못된 요청 400, 미지원 메서드 405, AI 서버 오류, 내부 오류 500을 공통 응답으로 감싼다.
- 인증·사용자·챌린지·리포트 도메인은 별도 오류 코드/처리기도 가진다.
- FastAPI는 입력 형식 오류를 422와 상세 항목으로 반환한다.

관련 파일: `frontend/src/api/httpClient.js`, `frontend/src/commonUtils/apiError.js`, `backend/src/main/java/com/wallo/common/exception/GlobalExceptionHandler.java`, `backend/src/main/java/com/wallo/*/exception`, `ai/app/application.py`

### 파일 업로드

- 프로필은 JPG/PNG, 최대 5MB를 프론트에서 검사하며 서버도 별도 검증·저장한다.
- 피드는 multipart 이미지와 내용을 받는다. Spring 전체 multipart 한도는 파일 50MB, 요청 약 52.5MB다.
- 관련 파일: `frontend/src/views/user/ProfileSettingsView.vue`, `frontend/src/api/feedApi.js`, `backend/src/main/webapp/WEB-INF/web.xml`, `backend/src/main/java/com/wallo/user/service/UserProfileService.java`, `backend/src/main/java/com/wallo/feed/controller/FeedController.java`

## 7. 아직 미완성, 목업, 하드코딩, 잠재 버그로 보이는 부분

### 명확한 미완성·목업

1. **오늘의 미션은 미구현이다.** API가 빈 배열을 반환하고 화면에 “추후에 추가 예정”만 표시한다.  
   관련 파일: `frontend/src/api/missionApi.js`, `frontend/src/components/navigation/TopHeader.vue`
2. **외부 금융기관 연동 기본값은 목업이다.** 계좌·카드·증권·소득 데이터가 `backend/src/main/resources/mock/codef/*.json`에서 나온다. 실제 CODEF 모드 설정은 있으나 실제 사용자별 인증정보 저장·운영 검증은 코드상 확인되지 않음.  
   관련 파일: `backend/src/main/java/com/wallo/config/AppConfig.java`, `backend/src/main/java/com/wallo/external`, `backend/src/main/resources/mock/codef`
3. **피드 AI 분석 기본 구현이 목업이다.** `AppConfig`가 `MockFeedAnalysisClient`를 빈으로 선택한다. Gemini/검색 가격 수집 구현은 존재하지만 운영 설정에 실제로 선택되는 조건은 제한적이며 현재 기본 실행에서 사용 여부는 코드상 확인되지 않음.  
   관련 파일: `backend/src/main/java/com/wallo/config/AppConfig.java`, `backend/src/main/java/com/wallo/feed/analysis/MockFeedAnalysisClient.java`, `GeminiFeedAnalysisClient.java`
4. **AI 금융 상담 도구 일부는 `pending_integration`을 반환하도록 설계돼 있다.** 어떤 버튼/질문에서 사용자에게 노출되는지는 실행 데이터 없이는 코드상 확정되지 않음.  
   관련 파일: `ai/app/agents/financial/tools/pending.py`, `ai/app/ARCHITECTURE.md`
5. **AI 뉴스 리포트는 환경변수로 목업 전환 가능**하며 실제 키가 없으면 정상 생성되지 않는다.  
   관련 파일: `ai/.env.example`, `ai/app/reports/router.py`
6. **AI 데모 API는 존재하지만 실제 프론트 화면 연결은 확인되지 않는다.**  
   관련 파일: `ai/app/demo`, `frontend/src/api`(대응 호출 없음)

### 하드코딩·운영 위험

1. **DB 비밀번호가 저장소 설정 파일에 평문으로 들어 있다.** 즉시 환경변수나 로컬 비추적 파일로 옮기고 노출된 비밀번호를 교체해야 한다. `RUNBOOK.md` 설명(기본 비밀번호는 비어 있음)과 실제 파일도 불일치한다.  
   관련 파일: `backend/src/main/resources/application.properties`, `RUNBOOK.md`
2. AI 서버 기본 주소, CODEF 목업 주소, 개발 CORS가 localhost에 맞춰져 있다. 배포 환경에서는 명시적 설정이 필요하다.  
   관련 파일: `backend/src/main/java/com/wallo/chat/client/PythonAiClient.java`, `asset/client/PythonAssetReportAiClient.java`, `report/client/PythonNewsReportAiClient.java`, `backend/src/main/java/com/wallo/config/WebMvcConfig.java`
3. 금융 리포트 보관 기간은 기본 3일이고 목록은 리포트가 이미 생성된 뉴스만 보여준다. 데이터가 적어 보일 수 있으나 현재 코드상 의도된 동작이다.  
   관련 파일: `backend/src/main/resources/application.properties`, `backend/src/main/resources/mapper/report/NewsMapper.xml`
4. 읽은 리포트 상태는 서버가 아니라 브라우저 `localStorage`에만 저장되어 기기·브라우저 간 공유되지 않는다.  
   관련 파일: `frontend/src/utils/report/reportReadState.js`
5. 포인트샵·랭킹에는 운영 화면에서 노출되는 개발용 즉시 보상 API/버튼이 있다. 배포 전 제거하거나 관리자 권한으로 제한해야 한다.  
   관련 파일: `frontend/src/views/challenge/ChallengeRankingView.vue`, `frontend/src/api/challengeApi.js`, `backend/src/main/java/com/wallo/challenge/controller/ChallengeController.java`
6. 크롤러 점검용 `/api/test/crawl...`와 리포트 수동 생성 API에 별도 관리자 권한이 확인되지 않는다. 비용·부하를 유발할 수 있다.  
   관련 파일: `backend/src/main/java/com/wallo/report/controller/CrawlTestController.java`, `ReportController.java`

### 잠재 버그·불일치

1. **피드 좋아요·수정·삭제 실패 처리에서 정의되지 않은 `message()`를 호출한다.** 해당 API가 실패하면 원래 서버 오류 대신 `ReferenceError`가 발생할 가능성이 높다. 같은 파일의 다른 함수는 `getApiErrorMessage()`를 올바르게 쓴다.  
   관련 파일: `frontend/src/api/feedApi.js`
2. **`financial-report.worker.interval-ms`가 설정 파일에 두 번 선언되어 있다.** 값은 같아 당장 결과는 같지만 유지보수 혼란 요인이다.  
   관련 파일: `backend/src/main/resources/application.properties`
3. **RUNBOOK의 금융용어 캐시 설명이 현재 설정과 어긋날 수 있다.** RUNBOOK은 재시작 전까지 갱신되지 않는다고 쓰지만 `application.properties`에는 1시간 갱신 주기와 관련 설정이 있다. 실제 스케줄 연결 여부를 배포 전 다시 검증해야 한다.  
   관련 파일: `RUNBOOK.md`, `backend/src/main/resources/application.properties`, `backend/src/main/java/com/wallo/report/term/service/FinancialTermMatchingServiceImpl.java`
4. **전체 운영 DB 스키마/마이그레이션이 저장소에 없다.** 금융 리포트·소비 분석 일부와 테스트용 자산 스키마만 있어 새 팀원이 빈 DB에서 전체 서비스를 재현하기 어렵다.  
   관련 파일: `database/sql`, `backend/src/test/resources/db/h2/asset-mapper-schema.sql`
5. **응답 포맷이 도메인별로 통일되지 않았다.** 일부는 `CommonResponse.data`, 일부는 원본 DTO라 프론트에서 `response.data?.data ?? response.data` 같은 방어 코드가 반복된다. 기능 오류는 아니지만 변경 시 실수 위험이 있다.  
   관련 파일: `frontend/src/api/reportApi.js`, `frontend/src/api/userApi.js`, `frontend/src/api/connectionApi.js`, 각 Controller
6. **라우터 파일 들여쓰기가 일부 어긋나 있다.** 실행에는 영향이 없을 수 있으나 코드 품질 검사에서 걸릴 수 있다.  
   관련 파일: `frontend/src/router/index.js`
7. **`SessionCurrentUserProvider` 주석은 로그인 기능이 미래 작업인 것처럼 남아 있으나 실제 로그인은 구현돼 있다.** 동작 문제가 아니라 낡은 설명이다.  
   관련 파일: `backend/src/main/java/com/wallo/auth/SessionCurrentUserProvider.java`, `backend/src/main/java/com/wallo/auth/controller/AuthController.java`
8. **WebSocket 허용 출처가 localhost로 제한돼 있다.** 배포 환경 도메인을 설정하지 않으면 챌린지 실시간 채팅 연결이 거절될 수 있다.  
   관련 파일: `frontend/vite.config.js`, `backend/src/main/java/com/wallo/config/WebMvcConfig.java`

## 8. 다른 사람에게 3분 안에 설명할 수 있는 발표용 스크립트

> Wallo는 자산 조회만 하는 앱이 아니라, 금융 데이터를 행동으로 연결하는 개인 금융 관리 서비스입니다.
>
> 사용자는 먼저 회원가입과 로그인을 하고, 은행·카드·증권 자산을 연결합니다. 연결이 끝나면 대시보드에서 총자산, 이번 달 예산과 지출, 카테고리별 소비, 저축 목표를 한눈에 볼 수 있습니다. 소비 내역 화면에서는 월별 달력과 거래 목록을 보고 카테고리별 예산도 직접 조정할 수 있습니다. 연봉을 입력하면 연말정산 공제 진행 상황도 보여줍니다.
>
> 두 번째 핵심은 AI 상담입니다. 사용자는 여러 상담방을 만들고 금융 질문을 하거나 소비 분석을 받을 수 있습니다. 특히 목표 설정 대화를 진행하면 목표 금액과 기간을 정리하고, 서버가 목표와 3단계 실행 로드맵을 저장합니다. 사용자는 실제 계좌를 목표에 연결하고 단계별 완료 여부를 체크할 수 있습니다.
>
> 세 번째는 절약 챌린지입니다. 혼자 또는 그룹 챌린지를 만들고 초대 코드로 참여합니다. 사진과 절약 내용을 피드에 올리고, 좋아요와 그룹 메시지를 주고받으며, 주간 절약 순위를 확인합니다. 활동 보상으로 받은 포인트는 포인트샵에서 랜덤박스를 여는 데 쓰고, 모든 증감은 포인트 내역에 남습니다.
>
> 마지막으로 금융 뉴스 리포트가 있습니다. 서버가 뉴스를 수집하고 AI가 쉬운 요약과 영향, 행동 제안을 만들며, 어려운 금융용어는 기사 안에서 눌러 뜻을 볼 수 있습니다.
>
> 기술 흐름은 Vue 화면이 Spring Legacy 서버를 호출하고, Spring은 MyBatis로 MySQL을 사용합니다. AI가 필요한 경우에만 별도 FastAPI 서버를 거쳐 Groq를 호출합니다. 로그인은 서버 세션 방식입니다.
>
> 현재 주의할 점도 있습니다. 금융기관 데이터와 피드 AI 분석은 기본 실행에서 목업이고, 오늘의 미션은 아직 비어 있습니다. DB 비밀번호 평문 저장, 피드 오류 처리의 정의되지 않은 함수, 공개된 테스트성 API도 정리해야 합니다. 따라서 지금 상태는 주요 사용자 여정은 넓게 구현돼 있지만, 실제 외부 연동과 운영 보안 마무리가 필요한 단계라고 설명할 수 있습니다.

---

## 빠른 코드 탐색 순서

처음 보는 팀원은 아래 순서로 보면 전체 흐름을 가장 빨리 이해할 수 있다.

1. 화면 주소: `frontend/src/router/index.js`
2. 화면 행동: `frontend/src/views`
3. 서버 호출: `frontend/src/api`, `frontend/src/stores`
4. API 입구: `backend/src/main/java/com/wallo/*/controller`
5. 실제 업무 규칙: `backend/src/main/java/com/wallo/*/service`
6. DB 쿼리: `backend/src/main/resources/mapper`
7. AI 처리: `backend/src/main/java/com/wallo/*/client` → `ai/app`
8. 실행과 리포트 데이터 준비: `RUNBOOK.md`, `database/sql/README.md`
