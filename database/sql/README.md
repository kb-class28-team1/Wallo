# database/sql

Wallo 프로젝트의 MySQL 스키마를 Git으로 공유하기 위한 SQL 스크립트 모음입니다.
Spring Boot의 자동 스키마 생성 기능을 사용하지 않으므로(Spring Legacy + MyBatis), DB 스키마는 이 폴더의 SQL을 팀원이 각자 로컬 MySQL에 수동으로 적용하는 방식으로 관리합니다.

※ 공통 데이터베이스 생성은 팀에서 별도로 관리합니다.
※ 이 폴더에는 금융 리포트 기능의 테이블 생성 SQL만 포함합니다.

## 파일 구성

```
database/sql/
├── news.sql
├── news_report.sql
├── financial_term.sql
├── news_term.sql
└── README.md
```

| 파일 | 역할 |
|---|---|
| `news.sql` | 원본 뉴스 기사 저장 테이블 |
| `news_report.sql` | AI가 생성한 금융 리포트 저장 테이블 (`news`와 1:1, `news_id`에 FK + UNIQUE) |
| `financial_term.sql` | 금융 용어 사전 테이블 |
| `news_term.sql` | `news`와 `financial_term`의 다대다 연결 테이블 (복합 PK, 양쪽에 FK) |

모든 SQL은 재실행해도 에러가 나지 않도록 `IF NOT EXISTS`를 사용하고, 파일 상단에 `USE wallo;`를 포함합니다.

## 실행 순서

```
news.sql
   ↓
financial_term.sql
   ↓
news_report.sql
   ↓
news_term.sql
```

MySQL 서버 연결 → `wallo` 데이터베이스 선택 → `news.sql` → `financial_term.sql` → `news_report.sql` → `news_term.sql` 순서로 실행 → 테이블 생성 확인

### 실행 순서가 필요한 이유 (외래키 의존관계)

MySQL(InnoDB)은 `FOREIGN KEY`가 참조하는 테이블과 컬럼이 이미 존재해야 `CREATE TABLE`이 성공합니다. 참조 대상이 없는 상태에서 FK가 걸린 테이블을 먼저 만들면 에러가 발생합니다. 각 테이블의 의존관계는 다음과 같습니다.

- `news`: 다른 테이블을 참조하지 않음 → 가장 먼저 생성 가능
- `financial_term`: 다른 테이블을 참조하지 않음 → `news`와 마찬가지로 먼저 생성 가능 (`news`보다 먼저 실행해도 무방)
- `news_report`: `news_id`가 `news.news_id`를 참조(FK) → **`news.sql` 생성 후**에 실행해야 함
- `news_term`: `news_id`가 `news.news_id`를, `term_id`가 `financial_term.term_id`를 각각 참조(FK) → **`news.sql`과 `financial_term.sql` 생성 후**에 실행해야 함

즉 `news`와 `financial_term`은 순서가 서로 바뀌어도 상관없지만, `news_report`와 `news_term`은 반드시 자신이 참조하는 테이블보다 나중에 실행되어야 합니다. 위에 제시한 순서(`news` → `financial_term` → `news_report` → `news_term`)를 따르면 항상 안전합니다.

### 1. IntelliJ에서 MySQL 서버 연결

1. IntelliJ 오른쪽 **Database** 탭 열기 (없다면 `View → Tool Windows → Database`)
2. `+` → `Data Source` → `MySQL` 선택
3. Host / Port / User / Password 입력
4. `Test Connection`으로 연결 확인 후 `OK`
   - 드라이버가 없다는 안내가 뜨면 IntelliJ가 제공하는 다운로드 링크로 MySQL 드라이버를 받으면 됩니다.

### 2. `wallo` 데이터베이스 선택

1. `wallo` 데이터베이스는 팀에서 별도로 관리하는 공통 생성 스크립트로 미리 만들어져 있어야 합니다. 아직 없다면 먼저 해당 스크립트를 적용받으세요.
2. Database 탭에서 데이터 소스를 우클릭 → `Refresh` (또는 새로고침 아이콘) 후 `wallo`가 보이는지 확인
3. 데이터 소스가 특정 스키마만 보여주도록 설정돼 있다면, 데이터 소스 우클릭 → `Properties` → `Schemas` 탭에서 `wallo` 체크박스를 켜서 보이게 합니다.
4. 콘솔에서 `USE wallo;`가 자동으로 적용되도록, 좌측 상단 스키마 선택 드롭다운(또는 콘솔 상단)에서 데이터베이스를 `wallo`로 변경합니다.

### 3. 테이블 SQL 순서대로 실행

위 "실행 순서" 항목의 순서(`news.sql` → `financial_term.sql` → `news_report.sql` → `news_term.sql`)대로 각 파일을 열어 전체 스크립트를 실행합니다.

### 4. 테이블 생성 결과 확인

```sql
USE wallo;

SHOW TABLES;

DESCRIBE news;
DESCRIBE news_report;
DESCRIBE financial_term;
DESCRIBE news_term;

SHOW CREATE TABLE news;
SHOW CREATE TABLE news_report;
SHOW CREATE TABLE financial_term;
SHOW CREATE TABLE news_term;
```

- `news.url`, `financial_term.term_name`에 `UNIQUE` 인덱스가 걸려 있는지
- `news_report.news_id`에 `UNIQUE` + `FOREIGN KEY`가 걸려 있는지
- `news_term`이 `(news_id, term_id)` 복합 `PRIMARY KEY`와 양쪽 `FOREIGN KEY`를 모두 가지고 있는지

`SHOW CREATE TABLE` 결과로 확인하면 됩니다.

## 앞으로 팀원이 새 테이블을 추가하는 방법

담당 테이블이 생기면 `database/sql/` 아래에 **테이블명.sql** 파일을 새로 추가합니다. 예:

```
database/sql/
├── news.sql
├── news_report.sql
├── financial_term.sql
├── news_term.sql
├── users.sql
├── goals.sql
├── products.sql
└── reports.sql
```

각 파일은 기존 파일과 같은 형식을 따릅니다.

- 파일 상단에 `USE wallo;` 포함
- `CREATE TABLE IF NOT EXISTS 테이블명 (...)` 사용 (재실행 안전)
- 문자셋/collation은 공통 데이터베이스 생성 스크립트와 동일하게 `utf8mb4` / `utf8mb4_0900_ai_ci` 사용
- 다른 테이블을 참조하는 FK가 있다면, 참조 대상 테이블이 먼저 생성된다는 전제하에 README의 "실행 순서"에 추가하고 문서화

한 파일에 여러 테이블을 몰아넣지 말고 테이블 1개당 파일 1개로 유지해 주세요. 이렇게 하면 다른 팀원이 자기 테이블 파일을 추가할 때 같은 파일을 수정하며 생기는 Git 충돌을 피할 수 있습니다.

## 다른 팀원이 Git Pull 후 동일한 DB 구조를 만드는 방법

1. `git pull`로 최신 코드(및 이 `database/sql/` 폴더)를 받습니다.
2. 각자 로컬(또는 팀 공용) MySQL 서버에 IntelliJ Database로 연결합니다 (위 1번 과정과 동일).
3. 팀에서 별도로 관리하는 공통 데이터베이스 생성 스크립트를 적용받아 `wallo` 데이터베이스가 있는지 확인합니다.
4. IntelliJ Database를 새로고침하고 `wallo`를 선택합니다.
5. 위 "실행 순서"에 따라 `news.sql` → `financial_term.sql` → `news_report.sql` → `news_term.sql`을 순서대로 실행합니다. FK로 연결되지 않은 새 테이블이 추가돼 있다면 그 파일은 순서 상관없이 실행해도 됩니다.
6. 이미 스키마가 존재하는 팀원도 `IF NOT EXISTS` 덕분에 에러 없이 그대로 실행할 수 있습니다.

## 참고

- 이 단계에서는 Spring Batch/MyBatis 연동 코드나 애플리케이션의 DataSource 설정은 만들지 않았습니다. 이 SQL들은 IntelliJ Database 등 SQL 클라이언트로 직접 실행하는 용도입니다.
