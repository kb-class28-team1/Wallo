<div align="center">

# 💰 Wallo

**자산 연동부터 AI 금융 상담·목표 설계·소비 리포트·금융 챌린지까지,<br/>한 곳에서 관리하는 올인원 개인 금융 플랫폼**

[![Frontend](https://img.shields.io/badge/Frontend-Vue%203-42b883?style=flat&logo=vuedotjs&logoColor=white)]()
[![Backend](https://img.shields.io/badge/Backend-Spring%20Legacy%205.3-6DB33F?style=flat&logo=spring&logoColor=white)]()
[![AI](https://img.shields.io/badge/AI%20Server-FastAPI-009688?style=flat&logo=fastapi&logoColor=white)]()
[![DB](https://img.shields.io/badge/DB-MySQL%208-4479A1?style=flat&logo=mysql&logoColor=white)]()

</div>

<br/>

## 📌 목차

- [프로젝트 소개](#-프로젝트-소개)
- [주요 기능](#-주요-기능)
- [시스템 아키텍처](#-시스템-아키텍처)
- [기술 스택](#-기술-스택)
- [프로젝트 구조](#-프로젝트-구조)
- [데이터베이스 설계](#-데이터베이스-설계)
- [시작하기](#-시작하기)
- [환경 변수](#-환경-변수)
- [팀](#-팀)

<br/>

## 📝 프로젝트 소개

**Wallo**는 여러 금융기관에 흩어진 자산을 한 곳에 모으고, AI가 자산·소비 데이터를 분석해 맞춤형 조언을 제공하는 개인 금융 관리 서비스입니다.

정해진 입력 폼 대신 자연어 대화로 금융 목표를 설정하고, 실행 가능한 저축 계획을 받아볼 수 있습니다. 매일 수집되는 금융 뉴스는 AI가 자동으로 리포트를 생성하고 관련 금융용어를 함께 보여주며, 소비 습관 개선은 챌린지·미션 기반의 게이미피케이션으로 유도합니다.

<p align="center">
  <img src="docs/screenshots/onboarding-welcome.png" alt="Wallo 온보딩" width="700" />
</p>

<br/>

## ✨ 주요 기능

### 🏦 자산 연동
- 마이데이터(Codef 연동) 기반 계좌·카드 자산 통합 조회
- 자산·소비 내역 자동 분류 및 소비 리포트 생성

<p align="center">
  <img src="docs/screenshots/asset-connect-complete.png" alt="자산 연결 완료" width="700" />
</p>

### 🤖 AI 금융 챗봇
- Groq 기반 LLM Tool Calling으로 자산 분석, 소비 코칭, 금융상품 추천 등 목적별 응답 생성
- 채팅 첫 대화 내용을 기반으로 채팅방 제목 자동 생성

<p align="center">
  <img src="docs/screenshots/ai-chat.png" alt="AI 금융 챗봇" width="700" />
</p>

### 🎯 자연어 기반 금융 목표 설정
- 정해진 질문 순서 없이 자연어 한 문장에서 목표 금액·시점·현재 준비금 등을 한 번에 추출
- 부족한 정보만 이어서 질문하고, 목표일까지 필요한 월 납입액을 자동 계산
- Groq 응답 실패 시 로컬 규칙 기반 파서로 즉시 대체(불필요한 재호출 방지)

<p align="center">
  <img src="docs/screenshots/goal-setting.png" alt="금융 목표 설정" width="700" />
</p>

### 📰 금융 뉴스 리포트 & 금융용어 사전
- 뉴스 크롤링 → AI 리포트 생성 → 금융용어 매칭까지 자동 파이프라인(스케줄러 기반)
- 한국은행·금융감독원·재정경제부 용어사전(약 4,100건)을 기반으로 기사 속 금융용어를 자동 매칭
- 동일 뉴스라도 사용자 자산·소비 상황에 맞춰 영향도·대응전략을 개인화해서 별도 저장

<p align="center">
  <img src="docs/screenshots/financial-report-detail.png" alt="금융 리포트 상세" width="700" />
</p>

### 🏆 챌린지 & 미션
- 그룹/솔로 챌린지 생성 및 초대코드 참여, 절약 인증 피드 업로드
- AI 영상·이미지 분석으로 절약 여부·금액을 자동 판정하고, 사용자 피드백으로 결과를 검증·보정
- 인증 방식(AI 분석/거래내역/자가 체크)별 일일 미션과 포인트 적립, 포인트 상점
- 주간 랭킹(절약 금액·좋아요 수 기준)에 따라 순위별 포인트를 자동 지급

<p align="center">
  <img src="docs/screenshots/challenge-feed.png" alt="절약 챌린지 피드" width="700" />
</p>

### 🛍️ 금융상품 추천
- 금융상품 정보 기반 AI 추천

<p align="center">
  <img src="docs/screenshots/product-recommendation.png" alt="금융상품 추천" width="700" />
</p>

<br/>

## 🏗 시스템 아키텍처

```mermaid
flowchart LR
    User(["🧑‍💻 사용자<br/>Web Browser"])

    subgraph FE["프론트엔드 · JavaScript<br/>Vue 3 SPA"]
        direction TB
        FE1["Vue 3.5.13<br/>Composition API"]
        FE2["Vue Router 4.5.0"]
        FE3["Pinia 2.3.1<br/>전역 상태 관리"]
        FE4["Axios 1.7.9"]
        FE5["Bootstrap 5.3.3"]
        FE6["Chart.js 4.5.1"]
        FE7["Vite 6.4.3 · Vitest 4.1.10"]
    end

    subgraph BE["메인 백엔드 · Java 17<br/>Spring Legacy"]
        direction TB
        BE1["Spring Framework 5.3.39<br/>Java Config"]
        BE2["Spring MVC · WebSocket<br/>REST API · /ws"]
        BE3["JWT · BCrypt<br/>HMAC-SHA256 인증"]
        BE4["MyBatis 3.5.16<br/>Mapper Interface + XML"]
        BE5["Spring JDBC · HikariCP"]
        BE6["Spring Batch 4.3.10<br/>스케줄러"]
        BE7["Selenium · Springfox<br/>크롤링 · Swagger"]
        BE8["Tomcat 9 · Gradle WAR"]
    end

    DB[("MySQL 8<br/>wallo 스키마<br/>Connector/J 8.4.0")]

    subgraph AI["AI 서버 · Python<br/>도메인별 Agent"]
        direction TB
        AI1["FastAPI · Uvicorn"]
        AI2["Pydantic 요청 검증"]
        AI3["Groq Python SDK"]
        AI4["Domain AI Agents<br/>금융상담·목표·카테고리·자산리포트·미션"]
        AI5["Tool Registry"]
    end

    subgraph EXT["외부 서비스"]
        direction TB
        EXT1["Groq API · LLM<br/>AI 응답 생성"]
        EXT2["Google Gemini API<br/>피드 사진·영상 분석"]
        EXT3["SerpApi<br/>상품·음식 시세 검색"]
        EXT4["매일경제 뉴스<br/>크롤링"]
        EXT5["CODEF 계좌 연동<br/>(현재: Local Mock)"]
    end

    User --> FE1
    FE4 -- "REST · JSON" --> BE2
    FE2 -. "WebSocket · /ws" .-> BE2
    BE5 -- "JDBC · SQL" --> DB
    BE2 -- "HTTP · JSON" --> AI1
    AI3 -- "HTTPS · LLM" --> EXT1
    BE7 -. "뉴스 수집" .-> EXT4
    BE1 -. "Gemini · SerpApi · CODEF 호출" .-> EXT
```

프론트엔드는 백엔드와만 통신하며, 백엔드가 AI 서버·외부 API·DB를 중계하는 구조입니다.

**아키텍처 원칙**
- **Spring Legacy 5.3 · Java Config** — Spring Boot 자동설정 없이 WAR로 빌드해 외부 Tomcat에 배포
- **JPA/Hibernate 미사용** — DB 접근은 MyBatis Mapper Interface + XML로만 처리
- **AI 서버와 데이터베이스 분리** — AI 서버는 DB에 직접 접근하지 않고, 백엔드가 필요한 컨텍스트만 HTTP·JSON으로 전달
- **외부 연동 상태 구분** — CODEF 계좌 연동은 현재 Local Mock API이며, 실연동은 별도 설정 필요

<br/>

## 🛠 기술 스택

### Frontend

| 분류 | 기술 |
| --- | --- |
| Framework | Vue 3 (Composition API, `<script setup>`) |
| Build Tool | Vite |
| State | Pinia |
| Styling | Bootstrap, Bootstrap Icons |
| HTTP | Axios |
| Chart | Chart.js, vue-chartjs |
| Test | Vitest, @vue/test-utils |

### Backend

| 분류 | 기술 |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Framework 5.3.x (Legacy, Java Config 기반) |
| Persistence | MyBatis |
| DB Driver / Pool | MySQL Connector/J, HikariCP |
| Batch | Spring Batch |
| Crawling | Selenium |
| Docs | Springfox (Swagger2) |
| Build | Gradle (WAR) |
| Test | JUnit 5, Mockito, H2 |

### AI Server

| 분류 | 기술 |
| --- | --- |
| Framework | FastAPI, Uvicorn |
| LLM | Groq (Tool Calling 기반 Agent) |
| 기타 | 금융용어 크롤러/변환기(requests, BeautifulSoup, Selenium, pandas, PyMuPDF) |
| Test | pytest |

### Database & Infra

| 분류 | 기술 |
| --- | --- |
| DB | MySQL 8 |
| WAS | Tomcat 9 |

<br/>

## 📁 프로젝트 구조

```
Wallo/
├── frontend/                # Vue 3 클라이언트
│   └── src/
│       ├── api/             # Axios 인스턴스 및 API 모듈
│       ├── components/      # 재사용 컴포넌트
│       ├── composables/     # Composition API 훅
│       ├── features/        # 자산/금융 도메인 로직
│       ├── router/          # Vue Router 설정
│       ├── stores/          # Pinia 스토어
│       └── views/           # 페이지 컴포넌트
│           ├── ai/          # AI 챗봇
│           ├── analysis/    # 자산·소비 분석
│           ├── asset/       # 자산 연동
│           ├── auth/        # 로그인/인증
│           ├── challenge/   # 챌린지
│           ├── chat/        # 채팅
│           ├── dashboard/   # 대시보드
│           ├── onboarding/  # 온보딩
│           ├── product/     # 금융상품
│           ├── report/      # 금융 뉴스 리포트
│           └── user/        # 마이페이지
│
├── backend/                 # Spring Legacy 서버 (도메인 단위 패키지)
│   └── src/main/java/com/wallo/
│       ├── asset/           # 자산 연동·분류
│       ├── auth/            # 인증
│       ├── challenge/       # 챌린지
│       ├── chat/            # 채팅
│       ├── feed/            # 피드
│       ├── goal/            # 금융 목표
│       ├── mission/         # 미션
│       ├── pointshop/       # 포인트 상점
│       ├── report/          # 금융 뉴스 리포트
│       ├── user/            # 사용자
│       ├── external/        # 외부 API 연동
│       └── config/          # Java Config
│
├── ai/                       # FastAPI AI 서버
│   └── app/
│       ├── agents/           # 도메인별 Agent·Tool (financial, goal, roadmap, category, asset_reports)
│       ├── chat/              # 채팅 API
│       ├── reports/           # 금융 뉴스 리포트 생성 API
│       ├── clients/           # Groq 클라이언트
│       └── core/              # 환경설정
│
└── database/                 # 스키마·시드 SQL
    ├── sql/
    └── seed/
```

<br/>

## 🗄 데이터베이스 설계

기능 도메인별로 테이블을 묶어서 본 개요입니다(발표용 요약이며, 도메인 간 연결선은 생략했습니다).

```mermaid
flowchart LR
    USERS(["👤 USERS<br/>모든 기능의 기준 사용자"])

    subgraph ASSET["자산 · 예산"]
        direction TB
        INSTITUTIONS["INSTITUTIONS<br/>금융기관 정보"] --> CONNECTIONS["CONNECTIONS<br/>계좌 연동 정보"]
        CONNECTIONS --> ACCOUNTS["ACCOUNTS<br/>연결된 계좌"]
        CONNECTIONS --> CARDS["CARDS<br/>연결된 카드"]
        ACCOUNTS --> TRANSACTIONS["TRANSACTIONS<br/>소비·입출금 내역"]
        CARDS --> TRANSACTIONS
        BUDGETS["BUDGETS<br/>예산 목표"] --> BUDGET_PLANS["BUDGET_PLANS<br/>예산 실행 계획"]
        BUDGET_PLANS --> BUDGET_PLAN_CATEGORIES["BUDGET_PLAN_CATEGORIES<br/>예산별 카테고리"]
        ASSET_SNAPSHOTS["ASSET_SNAPSHOTS<br/>월별 자산 스냅샷"]
        POINT_HISTORY["POINT_HISTORY<br/>포인트 적립·사용 이력"]
        USER_INVENTORY["USER_INVENTORY<br/>보상 보관함"]
    end

    subgraph AIGOAL["AI 상담 · 목표"]
        direction TB
        CONVERSATIONS["CONVERSATIONS<br/>AI 상담 세션"] --> CHAT_MESSAGES["CHAT_MESSAGES<br/>상담 메시지"]
        CHAT_MESSAGES --> ASSET_ANALYSIS["ASSET_ANALYSIS_RESULTS<br/>자산 분석 결과"]
        CHAT_MESSAGES --> CONSUMPTION_ANALYSIS["CONSUMPTION_ANALYSIS_RESULTS<br/>소비 분석 결과"]
        CHAT_MESSAGES --> PRODUCT_REC["PRODUCT_RECOMMENDATION_RESULTS<br/>상품 추천 결과"]
        GOAL_INTERVIEW["GOAL_INTERVIEW_SESSIONS<br/>목표 설문 세션"] --> FINANCIAL_GOALS["FINANCIAL_GOALS<br/>금융 목표"]
        FINANCIAL_GOALS --> GOAL_ROADMAPS["GOAL_ROADMAPS<br/>목표 달성 로드맵"]
        FINANCIAL_GOALS --> FINANCIAL_GOAL_ACCOUNTS["FINANCIAL_GOAL_ACCOUNTS<br/>목표 연결 계좌"]
    end

    subgraph CHALLENGE["챌린지 · 미션"]
        direction TB
        CHALLENGE_T["CHALLENGE<br/>챌린지 정보"] --> FEED["FEED<br/>인증 게시물"]
        FEED --> FEED_ANALYSIS["FEED_ANALYSIS<br/>인증 분석 결과"]
        FEED_ANALYSIS --> FEED_ANALYSIS_FEEDBACK["FEED_ANALYSIS_FEEDBACK<br/>분석 피드백"]
        CHALLENGE_T --> MESSAGE["MESSAGE<br/>챌린지 메시지"]
        DAILY_MISSIONS["DAILY_MISSIONS<br/>일일 절약 미션"] --> MISSION_VERIFICATIONS["MISSION_VERIFICATIONS<br/>미션 인증 기록"]
        FEED_PRICE_REFERENCE["FEED_PRICE_REFERENCE<br/>상품 시세 기준"]
        FEED_DISH_RECIPE["FEED_DISH_RECIPE_INGREDIENT<br/>음식 레시피 재료"]
        FEED_FOOD_COST["FEED_FOOD_COST_REFERENCE<br/>음식 재료비 기준"]
    end

    subgraph NEWS["뉴스 · 금융 용어"]
        direction TB
        NEWS_T["NEWS<br/>금융 뉴스 원문"] --> NEWS_REPORT["NEWS_REPORT<br/>AI 뉴스 리포트"]
        NEWS_T --> NEWS_REPORT_PERS["NEWS_REPORT_PERSONALIZATION<br/>사용자별 개인화 영향·대응전략"]
        NEWS_T --> NEWS_TERM["NEWS_TERM<br/>뉴스 연결 용어"]
        FINANCIAL_TERM["FINANCIAL_TERM<br/>금융 용어 사전"] --> NEWS_TERM
    end

    USERS --> ASSET
    USERS --> AIGOAL
    USERS --> CHALLENGE
    USERS --> NEWS
```

<br/>

## 🚀 시작하기

### 요구 사항

- Node.js 18+, npm
- JDK 17, Tomcat 9
- Python 3.x
- MySQL 8

### 1. 데이터베이스 준비

```bash
mysql -u root -p < database/dbinit.sql
mysql -u root -p wallo < ai/data/processed/financial_term_insert.sql
```

`backend/src/main/resources/application-local.properties`를 생성해 로컬 DB 비밀번호 등을 설정합니다(커밋 대상 아님).

### 2. AI 서버 실행

```bash
cd ai
python -m venv .venv
.venv\Scripts\activate        # Windows
pip install -r requirements.txt
cp .env.example .env          # GROQ_API_KEY 입력
uvicorn app.application:app --reload --port 8000
```

### 3. 백엔드 실행

Spring Boot가 아닌 WAR + 외부 Tomcat 구조로, IntelliJ의 Tomcat Server(Local) 실행 구성을 권장합니다. 수동 빌드 시:

```bash
cd backend
./gradlew build
# backend/build/libs/*.war 를 Tomcat 9의 webapps/ 에 배치
```

### 4. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:5173` 접속 (API 요청은 Vite 프록시를 통해 `http://localhost:8080`으로 전달됩니다.)

더 자세한 실행·트러블슈팅 가이드는 [RUNBOOK.md](RUNBOOK.md)를 참고하세요.

<br/>

## 🔑 환경 변수

| 위치 | 파일 | 용도 |
| --- | --- | --- |
| Backend | `application-local.properties` | DB 접속 정보 등 로컬 값(git 추적 제외) |
| AI Server | `ai/.env` (`ai/.env.example` 참고) | `GROQ_API_KEY`, `GROQ_MODEL`, `AI_REPORT_MOCK_ENABLED` 등 |

> ⚠️ 실제 API 키·비밀번호는 절대 커밋하지 마세요.

<br/>

## 👥 팀

<!-- 팀원 정보를 입력하세요 -->

| 이름 | 역할 | GitHub |
| --- | --- | --- |
|  |  |  |
|  |  |  |
|  |  |  |
