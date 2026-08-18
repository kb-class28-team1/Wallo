# 소비분석 에이전트·금융리포트 에이전트 파일 가이드

> 목적: Codex 대화로 구현한 **소비분석 에이전트**와 **금융리포트 에이전트**에 대해, 팀원이 “이 폴더/파일은 왜 있나요?”라고 물었을 때 파일 단위로 답하기 위한 설명서  
> 분석 기준: `backend/src/main`, `ai/app`, `frontend/src`, MyBatis XML, DB SQL, 테스트 코드의 실제 연결 관계  
> 확인 일자: 2026-08-13
>
> 실제 코드 리뷰 발표 순서, 설계 이유, 경계 상황, 테스트와 미완성 범위는 `CODE_REVIEW_PREP.md`를 참고한다.

## 0. 이 문서의 범위

이 문서는 아래 두 영역만 설명한다.

- **소비분석 에이전트:** 거래를 기간별로 비교하고 카테고리·요일·시간대·예산 신호를 계산하며, 채팅에서 소비 질문에 답하는 기능
- **금융리포트 에이전트:** 금융 뉴스를 수집하고 AI 분석 리포트를 생성하며 금융용어를 연결하는 기능

다음 기능은 이름이 비슷해도 이 문서의 대상이 아니다.

- 자산 화면의 소비 리포트: `ai/app/asset_reports`, `ConsumptionReportCard.vue`
- 일반 자산분석: `tools/asset_analysis.py`
- 예·적금 상품 추천: `tools/product_recommendation.py`
- 목표 설정과 로드맵: `ai/app/agents/goal`, `tools/financial_goal.py`, `tools/goal_roadmap.py`
- 개인 종합 금융 리포트 자리: `tools/financial_report.py`

따라서 아래에서 “금융리포트”는 `ai/app/reports`의 **금융 뉴스 리포트**만 뜻한다.

---

# Part A. AI 채팅 소비 코치

> 현재 공식 소비분석 실행 경로는 Python 규칙 계산과 Spring 채팅 저장 구조다. 과거의 미사용 Java `com.wallo.spending` 계산 모듈과 `SPENDING_ANALYSES` 설계 SQL은 제거됐다.

## 1. `ai/app/agents/financial` 폴더 역할

사용자 메시지를 보고 적합한 금융 도구를 선택하고, 도구 결과를 다시 자연어 답변으로 만드는 에이전트다.

### 루트 파일

| 파일 | 역할 |
|---|---|
| `agent.py` | Groq에 도구 목록과 대화를 보내고, 도구 호출 인자를 파싱하고, 실행 결과를 넣어 최종 답변을 생성하는 지휘자다. 소비 질문이면 `coach_spending`을 사용한다. |
| `prompts.py` | 금융 상담원의 말투, 사실성, 도구 사용 원칙을 정한 시스템 지침이다. |
| `spending_intent.py` | “이번 달 식비”, “지난번보다” 같은 표현을 찾아 소비 질문 여부와 요청 기간을 결정한다. 후속 질문 문맥도 고려한다. |
| `consumption_models.py` | Spring이 보낸 거래·예산·분석기간 문맥을 Pydantic으로 검증한다. |
| `__init__.py` | Python 패키지 표시 파일이다. |

### 소비분석과 직접 관련된 `tools` 파일

| 파일 | 역할 | 상태 |
|---|---|---|
| `spending_coach.py` | 거래를 요청 기간과 비교 기간으로 나누고 카테고리 증감, 반복 소비, 정기성 지출, 개선 신호, 예산 상태를 계산한다. | 실제 구현·채팅 연결 |
| `registry.py` | 여러 금융 도구 중 `coach_spending`을 찾아 실행하고 Spring에서 받은 소비 문맥을 넘긴다. 소비분석과 관련된 부분만 이 문서의 범위다. | 실제 연결 |
| `__init__.py` | Python 패키지 표시 파일이다. | 보조 파일 |

경로: `ai/app/agents/financial`, `ai/app/agents/financial/tools`

## 2. `spending_coach.py`가 실제로 계산하는 것

- 이번 달, 지난달, 최근 7일, 최근 30일, 직접 지정 기간을 해석한다.
- 현재 기간과 같은 길이의 비교 기간을 만든다.
- 카테고리별 금액·건수와 증감률을 계산한다.
- 카페·배달·쇼핑 등 조절 가능한 지출을 별도로 본다.
- 같은 가맹점/유사 주기의 반복 결제를 찾는다.
- 최근 두 달 개선 여부와 긍정 신호를 만든다.
- 월 예산이 있으면 사용률과 남은 금액을 계산한다.
- 데이터가 부족한 경우 과한 결론 대신 부족 상태를 반환한다.

Java `spending` 모듈과 계산 목적이 겹치지만 현재 서로를 호출하지 않는다. 즉 동일한 소비분석 규칙이 Java와 Python에 일부 중복되어 있다.

관련 파일: `ai/app/agents/financial/tools/spending_coach.py`, `ai/app/agents/financial/spending_intent.py`, `ai/app/agents/financial/consumption_models.py`

## 3. 채팅까지 연결되는 흐름

1. Vue `ChatView`가 Spring 대화 메시지 API를 호출한다.
2. Spring `ConsumptionAnalysisContextService`가 사용자의 거래·예산을 AI 요청 문맥으로 만든다.
3. `PythonAiClient`가 FastAPI `/api/chat`을 호출한다.
4. `ChatService`가 `FinancialAgent`를 실행한다.
5. 소비 질문이면 `spending_intent.py`가 기간 인자를 보강하고 `spending_coach.py`가 숫자 구조를 만든다.
6. Groq가 그 구조를 사용자용 자연어 답변으로 바꾼다.
7. Spring이 메시지와 소비분석 결과를 DB에 저장하고 Vue가 분석 블록을 렌더링한다.

관련 파일:

- 프론트: `frontend/src/views/chat/ChatView.vue`, `frontend/src/components/analysis`, `frontend/src/types/consumptionAnalysis.js`
- Spring: `backend/src/main/java/com/wallo/chat/service/ConversationMessageService.java`, `backend/src/main/java/com/wallo/asset/service/ConsumptionAnalysisContextService.java`, `backend/src/main/java/com/wallo/chat/client/PythonAiClient.java`, `backend/src/main/resources/mapper/chat/ConsumptionAnalysisResultMapper.xml`
- FastAPI: `ai/app/chat/router.py`, `ai/app/chat/service.py`, `ai/app/chat/schemas.py`, `ai/app/agents/financial`

## 4. 소비분석 프론트 표시 파일

| 파일 | 화면 역할 |
|---|---|
| `AnalysisResult.vue` | AI가 반환한 소비분석 전체 블록을 상태에 맞게 조합한다. |
| `SummaryMetrics.vue` | 총지출·거래 수·이전 대비 등 상단 숫자 |
| `BudgetGauge.vue` | 예산 사용률과 남은 금액 |
| `CategoryOverview.vue` | 카테고리별 소비와 변화 |
| `RecurringPatternCard.vue` | 반복/정기 소비 패턴 |
| `SpendingSignalList.vue` | 주의할 소비 신호 |
| `PositiveSignalList.vue` | 개선된 소비 신호 |
| `InsufficientDataNotice.vue` | 데이터 부족 안내 |
| `consumptionAnalysis.js` | 응답 형태와 기본값을 정리하는 프론트 타입/정규화 도우미 |

경로: `frontend/src/components/analysis`, `frontend/src/types/consumptionAnalysis.js`

---

# Part B. 금융 뉴스 리포트 에이전트

## 5. 전체 처리 흐름

`매일경제 크롤링 → NEWS 저장 → 생성 대기열 → FastAPI/Groq 분석 → NEWS_REPORT 저장 → 금융용어 매칭 → NEWS_TERM 저장 → Vue 목록/상세 표시`

세부 순서:

1. `NewsCrawlingScheduler`가 하루 4회 크롤러를 시작한다.
2. `NewsCrawler`가 경제·증권·부동산 섹션에서 각 최대 3개 기사를 읽고 정리한다.
3. `NewsService`가 URL 중복과 필수값을 확인한 뒤 `NEWS`에 저장한다.
4. 크롤링이 끝나면 `FinancialReportGenerationScheduler`가 생성 작업을 예약한다.
5. 워커가 리포트 없는 기사 한 건씩 `NewsReportGenerationService`에 넘긴다.
6. `PythonNewsReportAiClient`가 FastAPI `/api/reports/generate`를 호출한다.
7. FastAPI가 본문을 자르고 Groq에 구조화 JSON을 요청해 여섯 영역을 만든다.
8. Spring이 모든 필드가 채워졌는지 검증하고 `NEWS_REPORT`에 저장한다.
9. `FinancialTermMatchingService`가 제목·본문에서 사전 용어를 찾아 `NEWS_TERM`에 저장한다.
10. Vue 목록은 리포트가 있는 뉴스만 보여주고, 상세는 분석과 용어 설명을 함께 표시한다.

## 6. Spring 금융리포트 폴더별 역할

| 폴더 | 역할 |
|---|---|
| `controller` | 브라우저가 호출하는 리포트 API와 크롤러 시험 API |
| `crawler` | 외부 뉴스 사이트에서 원문 수집 |
| `scheduler` | 정해진 시간 수집, AI 생성 대기열, 오래된 데이터 삭제, 용어 캐시 갱신 |
| `service` | 뉴스 검증·조회와 AI 리포트 생성 전체 업무 규칙 |
| `client` | Spring과 FastAPI 사이 HTTP 통신 |
| `domain` | NEWS/NEWS_REPORT 및 목록 조회 결과의 Java 표현 |
| `dto/ai` | FastAPI 요청·응답 형태 |
| `dto/response` | Vue에 보낼 목록·상세·용어 형태 |
| `mapper` | NEWS와 NEWS_REPORT SQL 함수 명세 |
| `term/domain`, `term/mapper`, `term/service` | 금융용어 사전 로딩·문자열 매칭·뉴스 연결 저장 |

경로: `backend/src/main/java/com/wallo/report`

## 7. Spring 파일별 설명

### `controller`

| 파일 | 역할 |
|---|---|
| `ReportController.java` | 목록, 상세, 단일 리포트 생성, 즉시 크롤링, 미생성 리포트 생성 API를 제공한다. |
| `CrawlTestController.java` | Selenium 선택자와 기사 추출 결과를 점검하는 개발용 API다. 실제 서비스 Controller와 별도이며 관리자 제한은 확인되지 않는다. |

### `crawler`

| 파일 | 역할 |
|---|---|
| `NewsCrawler.java` | Headless Chrome으로 매일경제 경제·증권·부동산 목록과 상세를 읽는다. 광고/기자명/이메일 등을 제거하고 URL 중복을 건너뛴 뒤 저장된 뉴스 ID를 반환한다. |

주요 하드코딩: 출처는 매일경제, 섹션당 최대 3개, 페이지 타임아웃 30초, CSS 선택자와 URL 패턴은 사이트 구조에 의존한다.

### `scheduler`

| 파일 | 역할 |
|---|---|
| `NewsCrawlingScheduler.java` | KST 06·12·15·18시에 크롤링한다. 중복 실행을 막고 완료 후 리포트 생성 요청을 건다. |
| `FinancialReportGenerationScheduler.java` | 리포트 없는 뉴스 대기열을 관리한다. 기본 1분마다 한 건을 처리하고, 실패 뉴스는 같은 실행에서 반복하지 않는다. |
| `NewsRetentionScheduler.java` | 매일 00:10, 기본 최근 3일 밖의 `NEWS_TERM → NEWS_REPORT → NEWS`를 순서대로 삭제한다. |
| `FinancialTermCacheRefreshScheduler.java` | 기본 1시간마다 금융용어 전체 메모리 캐시를 다시 읽는다. 실패하면 기존 캐시를 유지한다. |

### `service`

| 파일 | 역할 |
|---|---|
| `NewsService.java` | 뉴스 저장·조회·목록·상세 기능의 계약이다. |
| `NewsServiceImpl.java` | 필수값 검증, URL 중복 확인, 최대 조회 수 제한, 뉴스·리포트·용어 조합, 목록 DTO 변환을 수행한다. |
| `NewsReportGenerationService.java` | “해당 뉴스 리포트를 없으면 만든다”는 계약이다. |
| `NewsReportGenerationServiceImpl.java` | 기존 리포트 재사용, 빈 본문 차단, AI 호출, 6개 필드 검증, 동시 INSERT 충돌 처리, 리포트 저장, 용어 매칭을 조율한다. |

`generateIfAbsent`는 이름 그대로 기존 리포트가 있으면 AI를 다시 호출하지 않는다. Controller 주석의 “재생성/교체” 표현과 달리 실제 구현은 기존 행을 재사용하고 용어만 다시 매칭한다.

### `client`

| 파일 | 역할 |
|---|---|
| `NewsReportAiClient.java` | AI 공급자를 바꿔도 Service를 유지하기 위한 인터페이스다. |
| `PythonNewsReportAiClient.java` | Java 요청을 JSON으로 바꿔 기본 `127.0.0.1:8000`에 전송하고, 타임아웃·HTTP 오류·잘못된 JSON을 프로젝트 오류로 변환한다. |

### `domain`

| 파일 | 역할 |
|---|---|
| `News.java` | 원문 뉴스 한 건: ID, 제목, 본문, 출처, URL, 카테고리, 게시/생성/수정 시각 |
| `NewsReport.java` | AI 결과 한 건: 요약, 사건 설명, 원인, 사회 영향, 사용자 영향, 대응 전략 |
| `NewsReportListItem.java` | NEWS와 NEWS_REPORT를 조인한 목록 전용 결과. 전체 본문 대신 카드에 필요한 값만 가진다. |

### `dto/ai`

| 파일 | 역할 |
|---|---|
| `NewsReportAiRequest.java` | Spring → FastAPI: 뉴스 ID, 제목, 본문, 카테고리, 출처, 게시 시각 |
| `NewsReportAiResponse.java` | FastAPI → Spring: 요약 목록, 사건 설명, 원인, 사회 영향, 사용자 영향, 대응 전략 |

### `dto/response`

| 파일 | 역할 |
|---|---|
| `ReportListResponse.java` | 목록 카드용 ID·제목·요약·카테고리·출처·URL·게시시각·분석 여부 |
| `ReportDetailResponse.java` | 뉴스 원문과 여섯 분석 영역, 매칭 용어를 한 응답으로 조합. DB의 줄바꿈 요약을 bullet 목록으로 다시 나눈다. |
| `MatchedTermResponse.java` | 용어 ID, 이름, 전체 정의, 짧은 정의, 출처를 프론트에 전달한다. |

### `mapper`

| 파일 | 역할 |
|---|---|
| `NewsMapper.java` | 뉴스 INSERT/중복/조회/목록/미생성 대상/보관기간 삭제 함수 명세 |
| `NewsReportMapper.java` | 뉴스 ID로 리포트 조회와 INSERT 함수 명세 |
| `FinancialTermMapper.java` | 금융용어 전체 조회 함수 명세 |
| `NewsTermMapper.java` | 뉴스의 기존 용어 연결 삭제, 일괄 저장, 매칭 용어 조회 함수 명세 |

### `term`

| 파일 | 역할 |
|---|---|
| `FinancialTerm.java` | 금융용어 이름·전체 설명·짧은 설명·출처 데이터 |
| `FinancialTermMatchingService.java` | 용어 매칭과 캐시 갱신 계약 |
| `FinancialTermMatchingServiceImpl.java` | 전체 사전을 메모리에 컴파일하고 제목·본문에서 경계를 고려해 용어를 찾은 뒤 NEWS_TERM을 교체 저장한다. 짧고 흔한 오탐을 줄이는 정규화 규칙이 있다. |

## 8. MyBatis XML 파일별 설명

| 파일 | 실제 SQL 역할 |
|---|---|
| `NewsMapper.xml` | NEWS 저장/조회, URL 중복 검사, NEWS_REPORT가 있는 최근 기사 목록, 리포트 없는 기사 ID, 보관기간 지난 연결/리포트/뉴스 삭제 |
| `NewsReportMapper.xml` | 뉴스별 리포트 조회와 신규 저장 |
| `FinancialTermMapper.xml` | 금융용어 전체를 캐시에 올리기 위한 조회 |
| `NewsTermMapper.xml` | 뉴스-용어 연결 교체 저장과 상세용 용어 조회 |

중요 동작:

- 목록은 `NEWS INNER JOIN NEWS_REPORT`라 AI 생성 전 뉴스는 보이지 않는다.
- 목록 SQL은 현재 날짜 포함 최근 3일에 해당하는 `INTERVAL 2 DAY` 조건을 직접 사용한다.
- 리포트 없는 대상은 본문이 비어 있으면 제외한다.
- `NEWS_TERM` 재매칭은 기존 연결을 모두 지운 뒤 batch INSERT한다.

경로: `backend/src/main/resources/mapper/report`

## 9. 금융리포트 DB 테이블

### `NEWS`

크롤링한 원문 저장소다. URL은 UNIQUE라 같은 기사를 중복 저장하지 않는다.

파일: `database/sql/news.sql`

### `NEWS_REPORT`

뉴스 1건당 AI 리포트 1건이다. `news_id`가 UNIQUE이자 외래키다. 요약 bullet은 별도 행이 아니라 줄바꿈으로 합쳐 `summary TEXT` 한 칸에 저장한다.

파일: `database/sql/news_report.sql`

### `FINANCIAL_TERM`

금융용어 사전이다. 용어 이름은 UNIQUE이며 전체 설명, 화면용 짧은 설명, 출처를 가진다.

파일: `database/sql/financial_term.sql`, 기존 DB 보정은 `database/sql/financial_term_add_short_definition.sql`

### `NEWS_TERM`

뉴스와 용어의 N:M 연결 테이블이다. `(news_id, term_id)`가 복합 PK라 같은 용어가 한 뉴스에 중복 연결되지 않는다.

파일: `database/sql/news_term.sql`

### 용어 데이터

- 소규모/초기 시드: `database/seed/financial_term_data.sql`
- 대량 가공 결과: `ai/data/processed/financial_term_insert.sql`
- 원천별 가공 스크립트: `ai/scripts/extract_bok_terms.py`, `crawl_fss_terms.py`, `extract_moef_terms.py`, `merge_financial_terms.py`, `process_financial_term_short_definitions.py`

## 10. FastAPI 금융 뉴스 리포트 파일

경로: `ai/app/reports`

| 파일 | 역할 |
|---|---|
| `router.py` | `/api/reports/generate` 요청/응답 모델, JSON Schema, 길이 제한, Groq 호출, 한 번의 JSON 검증 재시도, 목업 모드, 오류 응답을 담당한다. |
| `prompts.py` | 비개발자도 이해할 수 있는 요약·사건·원인·사회 영향·개인 영향·행동 전략을 쓰도록 지시하고, 긴 본문을 앞 4000자+뒤 1000자로 자른다. |
| `profile_repository.py` | `selected_asset_profiles.json`의 고정 프로필 ID 7을 읽어 사용자 영향 설명에 넣을 문맥을 만든다. 실제 로그인 사용자의 자산이 아니다. |
| `__init__.py` | Python 패키지 표시 파일이다. |

### AI 출력 검증

- 요약은 목록이며 최대 5개, 각 최대 200자다.
- 나머지 분석 필드는 각각 최대 1200자다.
- 구조화 JSON Schema를 요구하고 파싱 실패 시 한 번 재시도한다.
- `AI_REPORT_MOCK_ENABLED=true`이면 Groq 대신 목업 결과를 반환한다.
- 실제 Groq 실패를 자동 목업으로 숨기지는 않는다.

### 개인화의 현재 한계

뉴스 리포트의 `userImpact`는 실제 로그인 사용자별 자산을 Spring이 보내는 구조가 아니다. FastAPI가 고정된 데모 프로필 ID 7을 로드한다. 따라서 화면 문구가 개인화되어 보여도 현재는 **데모 프로필 기반 개인화**다.

관련 파일: `ai/app/reports/profile_repository.py`, `ai/data/processed/selected_asset_profiles.json`, `backend/src/main/java/com/wallo/report/dto/ai/NewsReportAiRequest.java`

## 11. 금융리포트 프론트 파일

| 파일 | 역할 |
|---|---|
| `frontend/src/api/reportApi.js` | 목록·상세·생성·즉시 크롤링·미생성 생성 API 호출과 오류 문구 |
| `ReportListView.vue` | 리포트 목록, 수동 수집/생성 진행 상태, 읽음 상태 표시 |
| `ReportDetailView.vue` | 뉴스와 분석 영역 표시, 용어 선택 패널, 읽음 저장 |
| `ReportListCard.vue` | 목록 카드 한 개 |
| `ReportSection.vue` | 사건·원인·영향·전략 섹션의 공통 표시 |
| `TermInfoPanel.vue` | 선택된 금융용어의 짧은/전체 정의와 출처 |
| `reportReadState.js` | 읽은 뉴스 ID를 브라우저 `localStorage`에 저장 |
| `termHighlight.js` | 기사 문장을 일반 텍스트와 클릭 가능한 용어 구간으로 분리 |
| `termDefinition.js` | 짧은 설명 우선 등 화면에 보여줄 정의 선택 |

경로: `frontend/src/views/report`, `frontend/src/components/report`, `frontend/src/utils/report`

## 12. 금융리포트 테스트 파일

| 파일 | 무엇을 보장하나 |
|---|---|
| `ReportControllerTest.java` | API 응답과 수동 작업 요청 |
| `NewsServiceImplTest.java` | 뉴스 검증, 조회, 목록·상세 조합 |
| `NewsReportGenerationServiceImplTest.java` | 기존 재사용, AI 응답 검증, 저장, 동시 충돌, 용어 매칭 |
| `FinancialTermMatchingServiceImplTest.java` | 용어 경계·정규화·오탐 방지·저장 |
| `NewsCrawlingSchedulerTest.java` | 스케줄 중복 방지와 생성 요청 연결 |
| `FinancialReportGenerationSchedulerTest.java` | 배치 대상, 성공/실패/스킵, 중복 실행 방지 |
| `NewsRetentionSchedulerTest.java` | 보관 기준일과 삭제 순서 |
| `FinancialTermCacheRefreshSchedulerTest.java` | 주기 캐시 갱신과 실패 격리 |
| `MatchedTermResponseTest.java` | 용어 응답 변환 |
| `NewsCrawlerManualRunner.java` | IDE에서 크롤링만 수동 실행 |
| `NewsCrawlingAndReportManualRunner.java` | IDE에서 실제 자동 흐름을 수동 재현 |

경로: `backend/src/test/java/com/wallo/report`

---

# Part C. 자주 받을 질문과 답변

## “금융리포트 생성 API를 다시 누르면 기존 리포트가 교체되나요?”

현재 Service는 `generateIfAbsent`라 기존 `NEWS_REPORT`가 있으면 AI를 재호출하거나 UPDATE하지 않는다. 금융용어 매칭만 다시 수행한다. 프론트/API 주석의 “재생성·교체”와 실제 동작이 다르다.

## “왜 뉴스 목록에는 방금 크롤링한 기사가 안 보일 수 있나요?”

목록 SQL이 `NEWS_REPORT`와 INNER JOIN하기 때문이다. AI 리포트 저장이 끝나야 목록에 나타난다.

## “금융용어는 AI가 찾나요?”

아니다. Spring이 DB 사전을 메모리에 올리고 제목·본문 문자열을 규칙으로 매칭한다. AI 장애와 무관하게 동작한다.

## “금융리포트는 사용자마다 다른가요?”

현재 뉴스 분석의 사용자 영향은 FastAPI가 고정 데모 프로필 ID 7을 사용한다. 로그인 사용자별 결과는 아니다.

## “리포트 데이터는 계속 쌓이나요?”

기본 설정에서는 최근 3일만 유지한다. 매일 00:10에 오래된 용어 연결, 리포트, 뉴스 순서로 삭제한다.

## 13. 팀원에게 설명할 때 쓸 짧은 요약

> 소비분석은 거래 원본을 그대로 AI에 맡기지 않고, 기간·총액·카테고리·요일·시간대·예산 신호 같은 숫자를 규칙으로 계산하도록 설계했습니다. Java `spending` 폴더는 그 계산 코어와 DB 설계이고, 아직 API와 저장 연결은 남아 있습니다. 현재 채팅에서 실제 동작하는 소비 코칭은 Python `coach_spending` 경로입니다.
>
> 금융리포트는 매일경제 뉴스를 수집해 원문을 저장하고, 백그라운드 워커가 FastAPI/Groq로 여섯 영역의 쉬운 리포트를 만든 뒤 DB에 저장합니다. 금융용어는 AI가 아니라 DB 사전과 Java 규칙으로 매칭합니다. 목록에는 리포트 생성이 끝난 기사만 나타나고 기본 3일간 유지됩니다. 현재 개인 영향 설명은 실제 사용자 자산이 아니라 고정 데모 프로필을 사용한다는 점이 가장 중요한 한계입니다.


