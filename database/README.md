# Wallo 데이터베이스 실행 안내

## 파일 구성

| 파일 | 역할                                                               |
| --- |------------------------------------------------------------------|
| `dbInit.sql` | `wallo` 데이터베이스 생성, 전체 테이블(36개)·뷰(1개)·외래키 생성, 로컬 개발용 샘플·기준 데이터 입력 |
| `financial_term_data.sql` | 금융용어 4,149건과 `short_definition` 데이터 입력                           |

두 파일은 다음 순서로 실행해야 합니다.

```text
1. dbInit.sql
2. financial_term_data.sql
```


## 1단계: 전체 데이터베이스 초기화

```bash
mysql -u root -p < database/dbInit.sql
```

주의: 기존 테이블을 삭제하므로 해당 테이블의 데이터가 모두 사라집니다. 로컬 개발·테스트 환경에서만 실행하고, 운영 데이터베이스에서는 실행하지 않습니다.

## 2단계: 금융용어 데이터 입력

1단계가 성공한 뒤 실행합니다.

```bash
mysql -u root -p wallo < database/financial_term_data.sql
```

`financial_term_data.sql`은 테이블을 추가로 만들지 않고, 이미 생성된 `FINANCIAL_TERM` 테이블에 4,149건을 입력합니다.
`ON DUPLICATE KEY UPDATE`를 사용하므로 같은 파일을 다시 실행하면 기존 금융용어 데이터가 최신 값으로 갱신됩니다.

## 3단계: Before/After 시연 데이터 입력

상품추천 및 가입 전후 화면을 촬영할 때는 다음 두 계정을 사용합니다.

| 구분 | 이메일 | 비밀번호 | 용도 |
| --- | --- | --- | --- |
| Before | `before@wallo.demo` | `12341234` | 금융상품 가입 전 상태. WON적금 계좌가 없습니다. |
| After | `after@wallo.demo` | `12341234` | 금융상품 가입 후 상태. WON적금 계좌와 증가한 자산·목표 진척도가 포함되어 있습니다. |

시연 데이터 파일은 [demo/before_user.sql](demo/before_user.sql)과 [demo/after_user.sql](demo/after_user.sql)입니다. `dbInit.sql`을 먼저 실행한 후 두 파일을 실행합니다.

```bash
mysql -u root -p wallo < database/demo/before_user.sql
mysql -u root -p wallo < database/demo/after_user.sql
```

Windows PowerShell에서 `<` 입력 리디렉션이 동작하지 않는 경우에는 다음처럼 실행합니다.

```powershell
Get-Content -Raw database/demo/before_user.sql | mysql -u root -p --default-character-set=utf8mb4 wallo
Get-Content -Raw database/demo/after_user.sql | mysql -u root -p --default-character-set=utf8mb4 wallo
```

두 SQL 파일은 고정된 시연용 사용자 ID를 기준으로 작성되어 있어, 같은 파일을 다시 실행해도 시연 데이터를 재구성할 수 있습니다. 실행 순서는 반드시 `dbInit.sql` → `before_user.sql` → `after_user.sql`로 유지합니다.

### 촬영 중 Before에서 After로 전환하는 방법

가장 안정적인 방법은 브라우저 프로필을 두 개 사용하는 것입니다.

1. 백엔드와 프론트엔드를 실행하고 브라우저 프로필 A에서 `before@wallo.demo`로 로그인합니다.
2. 프로필 A에서 `/dashboard`, `/ai-consulting`, `/ai-analysis`를 열어 소비 경고와 상품추천 전 상태를 촬영합니다.
3. 상품추천 글의 외부 금융상품 링크를 클릭한 뒤, 사용자가 금융기관에서 직접 WON적금에 가입했다고 설명합니다. 실제 외부 금융기관 가입 화면은 시연에 포함하지 않고 전환 장면으로 처리합니다.
4. 외부 가입 장면이 끝나면 프로필 B 또는 시크릿 창을 새로 열고 `after@wallo.demo`로 로그인합니다.
5. `/users/profile/connections`에서 `WON적금` 계좌가 연결된 것을 먼저 보여준 뒤 `/assets`와 `/dashboard`로 이동해 자산 증가와 목표 진척을 보여줍니다.

같은 브라우저에서 전환해야 한다면 Before 계정에서 로그아웃한 후 After 계정으로 로그인하고, 로그인 직후 `Ctrl+F5`로 새로고침합니다. Pinia 상태나 브라우저의 로그인 정보가 남아 있을 수 있으므로, 촬영에서는 두 프로필 또는 시크릿 창을 권장합니다. SQL 파일을 촬영 중간에 다시 실행하는 방식으로 사용자를 전환하지 말고, 로그인한 계정으로 전환해야 합니다.

현재 After 데이터에는 WON적금 계좌가 미리 포함되어 있습니다. 따라서 `/assets`에서 반드시 "신규 1건" 동기화가 발생하는 시나리오가 아니라, 연결관리 화면에서 이미 동기화된 `WON적금`을 확인하고 자산·목표 변화로 이어지는 시나리오입니다.

## 실행 결과 확인

```sql
SHOW TABLES;

SELECT table_type, COUNT(*) AS object_count
FROM information_schema.tables
WHERE table_schema = 'wallo'
GROUP BY table_type
ORDER BY table_type;

SELECT COUNT(*) AS total_count
FROM financial_term;

SELECT
    COUNT(*) AS total_count,
    SUM(short_definition IS NULL OR TRIM(short_definition) = '')
        AS missing_short_definition
FROM financial_term;

SELECT source, COUNT(*) AS term_count
FROM financial_term
GROUP BY source
ORDER BY source;
```

정상적인 초기 데이터 기준 기대값은 다음과 같습니다.

- `BASE TABLE`: `36`
- `VIEW`: `1`
- `financial_term.total_count`: `4,149`
- `missing_short_definition`: `0`

