# 금융 리포트 기능 실행 가이드

`뉴스 크롤링 → AI 리포트 생성 → 금융용어 매칭 → /reports 화면`까지 전체 흐름을 로컬에서 띄우고 확인하는 방법을 정리합니다. 백엔드(Tomcat), AI 서버(FastAPI), 프론트엔드(Vite) 3개 프로세스 + MySQL이 모두 필요합니다.

## 0. 필요한 로컬 값 (`application-local.properties`)

`backend/src/main/resources/application-local.properties`는 개인 로컬 값(DB 비밀번호 등)을 담는 파일로 `.gitignore` 대상이라 커밋되지 않습니다. `application.properties`보다 **나중에** 로드되어 값을 override합니다([AppConfig.java](backend/src/main/java/com/wallo/config/AppConfig.java) 참고). 파일이 없으면 앱 부팅에는 실패하지 않지만, 로컬 MySQL 비밀번호를 여기에 넣지 않으면 `application.properties`의 빈 값(`db.password=`)이 그대로 적용되어 `Access denied for user 'root'@'localhost' (using password: NO)` 오류가 납니다.

```properties
db.driverClassName=com.mysql.cj.jdbc.Driver
db.url=jdbc:mysql://localhost:3306/wallo?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
db.username=root
db.password=본인_로컬_MySQL_비밀번호
codef.mock-api.base-url=http://localhost:8080

financial-report.scheduler.enabled=false
financial-report.scheduler.batch-size=10
```

**주의**: 이 파일을 수정한 뒤에는 Tomcat을 재시작해야 값이 반영됩니다(Spring이 부팅 시점에 한 번만 읽음, 핫리로드 안 됨).

## 1. financial_term 데이터 적재 방법

`financial_term` 테이블이 비어 있으면 금융용어 매칭·모달 기능이 항상 빈 배열만 반환합니다(에러는 안 남, 조용히 0건).

```bash
mysql -u root -p wallo < ai/data/processed/financial_term_insert.sql
```

- `INSERT IGNORE` 방식이라 재실행해도 안전합니다(`financial_term.term_name`이 UNIQUE).
- 적재 후 검증: `ai/data/processed/financial_term_insert_verify.sql` 참고, 또는 직접 확인:
  ```sql
  SELECT source, COUNT(*) FROM financial_term GROUP BY source;
  -- 기대값: 한국은행 789 / 금융감독원 502 / 재정경제부 2858 (총 4,149, 기존 데이터가 없는 빈 테이블 기준)
  ```

**중요**: `FinancialTermMatchingServiceImpl`은 `financial_term` 전체를 최초 1회 메모리에 캐싱하고 이후로는 갱신하지 않습니다(`refreshCache()`가 어떤 스케줄러/엔드포인트에도 연결돼 있지 않음). **Tomcat이 이미 떠 있는 상태에서 `financial_term`을 새로 적재하면, 재시작 전까지는 캐시가 비어 있던 시점 그대로라 매칭이 전부 0건으로 나옵니다.** `financial_term`을 적재/변경한 뒤에는 Tomcat을 반드시 재시작하세요.

## 2. AI 서버 실행 방법

```bash
cd ai
python -m venv .venv          # 최초 1회
.venv\Scripts\activate        # Windows
pip install -r requirements.txt

cp .env.example .env          # 최초 1회, OPENAI_API_KEY 채우기
uvicorn app.application:app --reload --port 8000
```

- 헬스체크: `curl http://127.0.0.1:8000/api/health` → `{"status":"ok"}`
- API 문서: `http://127.0.0.1:8000/docs`
- `.env`의 `AI_REPORT_MOCK_ENABLED=true`면 `/api/reports/generate`가 실제 OpenAI 대신 `[MOCK]` 더미 응답을 반환합니다(OpenAI 키 없이 흐름만 확인할 때 유용). 기본값은 `false`(실제 OpenAI 호출).
- 백엔드는 기본적으로 `http://127.0.0.1:8000`을 바라봅니다(`AI_SERVER_URL` 환경변수로 변경 가능, [PythonNewsReportAiClient.java](backend/src/main/java/com/wallo/client/PythonNewsReportAiClient.java)).

## 3. Tomcat(백엔드) 실행 방법

이 프로젝트는 Spring Boot가 아니라 WAR + 외부 Tomcat 구조입니다.

- **IntelliJ (권장, 지금까지 써온 방식)**: Run/Debug Configuration에서 Tomcat Server(Local) 실행 구성으로 기동. `application-local.properties`를 바꾼 뒤에는 재시작.
- **수동으로 WAR만 빌드하려면**: `cd backend && ./gradlew build` → `backend/build/libs/*.war`를 별도 Tomcat 9의 `webapps/`에 배치.
- 확인: `curl http://localhost:8080/api/reports` → `{"success":true,"data":[...]}`

## 4. 프론트엔드 실행 방법

```bash
cd frontend
pnpm install   # 최초 1회
pnpm run dev
```

- 기본 주소: `http://localhost:5173`, `/api`는 Vite 프록시로 `http://localhost:8080`에 전달됩니다.
- 로그인 없이 화면만 확인하려면 `frontend/src/router/index.js`의 `/app` 그룹 `meta: { requiresAuth: true }`를 임시로 주석 처리(반드시 확인 후 원복, 커밋 금지).

## 5. `financial-report.scheduler.enabled=true`의 의미

[FinancialReportGenerationScheduler.java](backend/src/main/java/com/wallo/scheduler/FinancialReportGenerationScheduler.java)가 news_report 없는 뉴스를 찾아 AI 리포트를 생성하는 배치를 실제로 동작시킬지 결정하는 스위치입니다.

- **기본값 `false`**: OPENAI_API_KEY 없는 로컬 환경에서 의도치 않게 AI가 호출되는 것을 막기 위한 안전장치입니다. `false`면 크롤링 스케줄러가 끝난 뒤 리포트 생성을 "시도"는 하지만, 대상 조회조차 없이 즉시 스킵되고 로그로만 안내됩니다.
- **`true`로 켜면**: (1) 하루 4회(06/12/15/18시) 크롤링 직후 자동으로 신규 뉴스의 리포트를 생성하고, (2) 그와 별개로 30분 뒤(06:30/12:30/15:30/18:30) 안전망 배치가 한 번 더 돌며 백로그(`batch-size`개 한도)를 처리합니다.
- 로컬에서 리포트 생성까지 실제로 확인하려면: `application-local.properties`에서 `true`로 바꾸고, AI 서버가 떠 있는지 확인한 뒤 Tomcat을 재시작하거나(자동 스케줄 대기) 아래 6번의 수동 Runner를 사용하세요. **확인이 끝나면 다시 `false`로 되돌리는 것을 권장합니다** (커밋 대상은 아니지만, 로컬에서 실수로 반복 AI 호출되는 것을 막기 위함).

## 6. 크롤링 + 리포트 생성을 한 번에 수동으로 확인하는 방법

Tomcat/크론을 기다리지 않고 지금 바로 전체 흐름(크롤링 → 크롤링 직후 리포트 생성 트리거)을 한 번 실행해보고 싶을 때 씁니다. `src/test/java`에 있어 실제 배포 산출물(WAR)에는 포함되지 않고, IDE에서 `main()`으로 직접 실행합니다.

- **크롤링만**: [NewsCrawlerManualRunner.java](backend/src/test/java/com/wallo/crawler/NewsCrawlerManualRunner.java) — `NewsCrawler.crawlAndSave()`만 호출, 리포트 생성 안 함.
- **크롤링 + 리포트 생성**: [NewsCrawlingAndReportManualRunner.java](backend/src/test/java/com/wallo/scheduler/NewsCrawlingAndReportManualRunner.java) — `NewsCrawlingScheduler.scheduledNewsCrawling()`을 그대로 호출해 실제 자동 실행과 동일한 흐름을 재현합니다.

실행 전 체크리스트:
1. `application-local.properties`의 `financial-report.scheduler.enabled=true`
2. `db.password` 채워짐
3. AI 서버(`http://127.0.0.1:8000`) 기동 중
4. IDE에서 해당 클래스를 우클릭 → Run (Tomcat 기동 여부와 무관하게 독립적인 Spring 컨텍스트를 새로 띄웁니다)

## 7. `/reports`에 기사가 안 보일 때 체크리스트

`/reports` 목록은 **news_report가 이미 생성된 기사만** 보여줍니다([NewsMapper.xml](backend/src/main/resources/mapper/NewsMapper.xml)의 `findAllWithReportSummary`가 INNER JOIN). 안 보인다면 순서대로 확인하세요.

1. **뉴스 자체가 저장됐는지**
   ```sql
   SELECT news_id, title, category, published_at FROM news ORDER BY news_id DESC LIMIT 10;
   ```
   없다면 크롤링이 아예 안 된 것 — 6번 Runner로 확인.

2. **news_report가 생성됐는지**
   ```sql
   SELECT n.news_id, n.category, (nr.news_id IS NOT NULL) AS has_report
   FROM news n LEFT JOIN news_report nr ON n.news_id = nr.news_id
   ORDER BY n.news_id DESC LIMIT 10;
   ```
   `has_report`가 0이면: (a) `financial-report.scheduler.enabled=false`였는지, (b) AI 서버가 안 떠 있었는지, (c) 본문이 비어 `REPORT_CONTENT_EMPTY`로 스킵됐는지 백엔드 로그 확인.

3. **API가 실제로 반환하는지**
   ```bash
   curl http://localhost:8080/api/reports
   ```
   `success:false`면 `error.code`로 원인 확인(대부분 DB 연결 문제 — 0번 참고).

4. **프론트가 백엔드를 제대로 바라보는지**
   - `http://localhost:8080/api/reports`는 되는데 화면엔 안 뜨면 Vite 프록시(`vite.config.js`)나 CORS/네트워크 탭 확인.
   - 로그인 가드 때문에 `/reports` 진입 자체가 막혔을 수 있음(4번 참고).

5. **상세 화면에서만 "아직 리포트가 준비되지 않았습니다"가 뜨는 경우**: 목록에 없던 뉴스를 URL로 직접 접근한 경우의 정상적인 예외 상태입니다([ReportDetailView.vue](frontend/src/views/report/ReportDetailView.vue)) — 버그 아님.

6. **금융용어 칩/모달이 안 뜨는 경우**: `news_term`이 비어 있을 수 있습니다 — 1번(캐시 갱신) 확인 후, 필요하면 `POST /api/reports/{newsId}/generate`를 다시 호출하면 news_report가 있어도 `news_term`만 재매칭됩니다.
   ```sql
   SELECT COUNT(*) FROM news_term WHERE news_id = ?;
   ```
