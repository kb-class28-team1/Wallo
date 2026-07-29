# backend/sql

Wallo 프로젝트의 MySQL 스키마를 Git으로 공유하기 위한 SQL 스크립트 모음입니다.
Spring Boot의 자동 스키마 생성 기능을 사용하지 않으므로(Spring Legacy + MyBatis), DB 스키마는 이 폴더의 SQL을 팀원이 각자 로컬 MySQL에 수동으로 적용하는 방식으로 관리합니다.

## 파일 구성

```
backend/sql/
├── create_database.sql
├── news.sql
└── README.md
```

| 파일 | 역할 |
|---|---|
| `create_database.sql` | `wallo` 데이터베이스 생성 (`utf8mb4` / `utf8mb4_0900_ai_ci`) 및 `USE wallo` |
| `news.sql` | `wallo` 데이터베이스 안에 `news` 테이블 생성 (`CREATE TABLE IF NOT EXISTS`) |

모든 SQL은 재실행해도 에러가 나지 않도록 `IF NOT EXISTS`를 사용합니다. 이미 적용된 환경에서 다시 실행해도 안전합니다.

## 실행 순서

MySQL 서버 연결 → `create_database.sql` 실행 → IntelliJ Database 새로고침 → `wallo` 선택 → `news.sql` 실행 → `news` 테이블 확인

### 1. IntelliJ에서 MySQL 서버 연결

1. IntelliJ 오른쪽 **Database** 탭 열기 (없다면 `View → Tool Windows → Database`)
2. `+` → `Data Source` → `MySQL` 선택
3. Host / Port / User / Password 입력 (Database 필드는 아직 `wallo`가 없으므로 비워두거나 기본값인 `mysql` 등으로 둬도 무방)
4. `Test Connection`으로 연결 확인 후 `OK`
   - 드라이버가 없다는 안내가 뜨면 IntelliJ가 제공하는 다운로드 링크로 MySQL 드라이버를 받으면 됩니다.

### 2. `create_database.sql` 실행

1. `backend/sql/create_database.sql` 파일을 IntelliJ에서 연 뒤, 방금 연결한 MySQL 데이터 소스를 우측 상단 콘솔 대상으로 선택
2. 전체 스크립트 실행 (`Ctrl+Enter` 또는 상단 실행 버튼)
3. 정상 실행되면 `wallo` 데이터베이스가 생성됩니다.

### 3. IntelliJ Database 새로고침 후 `wallo` 선택

1. Database 탭에서 방금 만든 데이터 소스를 우클릭 → `Refresh` (또는 새로고침 아이콘)
2. 데이터 소스 트리를 펼쳐서 `wallo`가 보이는지 확인
3. 데이터 소스가 특정 스키마만 보여주도록 설정돼 있다면, 데이터 소스 우클릭 → `Properties` → `Schemas` 탭에서 `wallo` 체크박스를 켜서 보이게 합니다.
4. 이후 콘솔에서 `USE wallo;`가 자동으로 적용되도록, 좌측 상단 스키마 선택 드롭다운(또는 콘솔 상단)에서 데이터베이스를 `wallo`로 변경합니다.

### 4. `news.sql` 실행

1. `backend/sql/news.sql` 파일을 열고, 콘솔 대상이 `wallo` 데이터베이스로 맞춰져 있는지 확인
2. 전체 스크립트 실행
3. 정상 실행되면 `wallo.news` 테이블이 생성됩니다.

### 5. 테이블 생성 결과 확인

```sql
USE wallo;

SHOW TABLES;

DESCRIBE news;

SHOW CREATE TABLE news;
```

`news_id`가 `PRIMARY KEY` + `AUTO_INCREMENT`이고, `url`에 `UNIQUE` 인덱스(`uk_news_url`)가 걸려 있는지 `SHOW CREATE TABLE news;` 결과로 확인하면 됩니다.

## 앞으로 팀원이 새 테이블을 추가하는 방법

담당 테이블이 생기면 `backend/sql/` 아래에 **테이블명.sql** 파일을 새로 추가합니다. 예:

```
backend/sql/
├── create_database.sql
├── news.sql
├── users.sql
├── goals.sql
├── products.sql
└── reports.sql
```

각 파일은 `news.sql`과 같은 형식을 따릅니다.

- 파일 상단에 `USE wallo;` 포함
- `CREATE TABLE IF NOT EXISTS 테이블명 (...)` 사용 (재실행 안전)
- 문자셋/collation은 `create_database.sql`과 동일하게 `utf8mb4` / `utf8mb4_0900_ai_ci` 사용

한 파일에 여러 테이블을 몰아넣지 말고 테이블 1개당 파일 1개로 유지해 주세요. 이렇게 하면 다른 팀원이 자기 테이블 파일을 추가할 때 같은 파일을 수정하며 생기는 Git 충돌을 피할 수 있습니다.

## 다른 팀원이 Git Pull 후 동일한 DB 구조를 만드는 방법

1. `git pull`로 최신 코드(및 이 `backend/sql/` 폴더)를 받습니다.
2. 각자 로컬(또는 팀 공용) MySQL 서버에 IntelliJ Database로 연결합니다 (위 1번 과정과 동일).
3. `create_database.sql`을 먼저 실행합니다.
4. IntelliJ Database를 새로고침하고 `wallo`를 선택합니다.
5. `backend/sql/` 아래의 테이블별 `.sql` 파일(`news.sql`, `users.sql` 등)을 각각 실행합니다 (테이블 간 참조 관계가 없다면 실행 순서는 상관없습니다).
6. 이미 스키마가 존재하는 팀원도 `IF NOT EXISTS` 덕분에 에러 없이 그대로 실행할 수 있습니다.

## 참고

- 이 단계에서는 Spring Batch/MyBatis 연동 코드나 애플리케이션의 DataSource 설정은 만들지 않았습니다. 이 SQL들은 IntelliJ Database 등 SQL 클라이언트로 직접 실행하는 용도입니다.
- AI 요약 등 추후 기능을 위한 컬럼은 `news.sql`에 포함하지 않았습니다.
