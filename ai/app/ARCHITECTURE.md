# Wallo AI 애플리케이션 구조

이 문서는 `ai/app`의 폴더 구조와 각 모듈의 책임, 새 Agent와 Tool을 추가하는 방법을 설명한다.
기능 코드를 `application.py`에 직접 추가하지 않고 아래 규칙에 따라 기능별 패키지로 분리한다.

## 현재 폴더 구조

```text
app/
├─ application.py              # FastAPI 앱 생성, 공통 예외 처리, Router 등록
├─ agents/                     # LLM이 Tool을 선택하고 실행하는 Agent
│  ├─ base.py                  # Tool 공통 실행 결과 모델
│  └─ financial/
│     ├─ agent.py              # 금융 Agent의 Tool Calling 흐름
│     ├─ prompts.py            # 금융 Agent 시스템 프롬프트
│     └─ tools/
│        ├─ registry.py        # Tool 명세와 실행 함수 등록
│        ├─ pending.py         # 미구현 Tool 공통 응답
│        ├─ asset_analysis.py
│        ├─ spending_coach.py
│        ├─ financial_goal.py
│        ├─ goal_roadmap.py
│        ├─ product_recommendation.py
│        └─ financial_report.py
├─ chat/
│  ├─ router.py                # `/api/chat` HTTP 요청·응답
│  ├─ schemas.py               # 채팅 Pydantic 요청·응답 모델
│  ├─ service.py               # 답변 및 제목 생성 흐름
│  ├─ title_service.py         # 첫 대화의 채팅방 제목 생성
│  └─ prompts.py               # 제목 생성 프롬프트
├─ category/
│  ├─ router.py                # `/api/category` HTTP 요청·응답
│  ├─ schemas.py               # 카테고리 분류 Pydantic 요청·응답 모델
│  ├─ service.py               # Groq 호출과 분류 결과 검증
│  └─ prompts.py               # 단건·배치 분류 프롬프트
├─ asset_reports/
│  ├─ router.py                # `/api/asset-reports/insights` HTTP 요청·응답
│  ├─ schemas.py               # 소비 리포트 Pydantic 요청·응답 모델
│  ├─ service.py               # Groq 호출과 응답 검증
│  └─ prompts.py               # 집계 소비 데이터 기반 생성 프롬프트
├─ clients/
│  ├─ groq_client.py           # Groq 클라이언트 생성
├─ core/
│  └─ config.py                # 환경변수와 모델 설정
├─ demo/
│  ├─ router.py                # 가상 사용자 데모 API
│  ├─ schemas.py               # 데모 요청·응답 모델
│  ├─ repository.py            # 데모 JSON 데이터 접근
│  └─ service.py               # 데모 자산 계산과 AI 분석
├─ health/
│  └─ router.py                # `/api/health` 상태 확인 API
└─ reports/
   ├─ router.py                # 금융 뉴스 리포트 생성 API
   └─ prompts.py               # 금융 뉴스 리포트 프롬프트
```

`__init__.py`와 Python이 자동 생성하는 `__pycache__`는 위 트리에서 생략했다.
`__pycache__`는 소스 코드가 아니므로 Git에 커밋하지 않는다.

## 모듈별 책임

### `application.py`

- FastAPI 인스턴스를 생성한다.
- 도메인 Router를 등록한다.
- 여러 기능이 공유하는 예외 처리와 앱 설정만 담당한다.
- 프롬프트, Pydantic 모델, AI 호출 또는 Tool 구현을 작성하지 않는다.

### `router.py`

- HTTP 경로, 상태 코드, 요청 및 응답 변환을 담당한다.
- 실제 기능은 Service 또는 Agent에 위임한다.
- Groq나 OpenAI SDK 호출 로직을 직접 구현하지 않는다.

### `schemas.py`

- 외부 API 요청 및 응답에 사용되는 Pydantic 모델을 정의한다.
- 내부 데이터 처리 모델과 API 모델의 역할이 달라지면 파일을 분리한다.

### `service.py`

- 하나의 기능을 완료하기 위한 처리 순서를 담당한다.
- Repository, Agent, 외부 Client를 조합한다.
- HTTP 요청 객체나 FastAPI에 직접 의존하지 않는 것을 원칙으로 한다.

### `repository.py`

- JSON, CSV, 데이터베이스 또는 외부 데이터 저장소 접근을 담당한다.
- 데이터 조회 방법과 비즈니스 판단을 분리한다.

### `clients/`

- Groq 등 외부 서비스 클라이언트를 생성한다.
- API 키와 모델명은 하드코딩하지 않고 `core/config.py`에서 읽는다.

### `agents/`

- 사용자 요청을 해석하고 사용할 Tool을 선택한다.
- Tool 실행 결과를 LLM에 전달하고 최종 답변을 만든다.
- 같은 목적을 가진 여러 Tool을 도메인 단위 Agent가 관리한다.

### `tools/`

- Tool 하나당 파일 하나를 사용한다.
- 각 Tool 파일은 `NAME`, `SCHEMA`, `execute`를 제공한다.
- Tool은 실제 계산, 조회 또는 도메인 서비스 호출을 담당한다.
- LLM이 제공한 사용자 ID나 권한 정보를 그대로 신뢰하지 않는다.

## 새 Tool 추가 방법

예를 들어 월별 예산 분석 Tool을 추가한다면 다음 파일을 만든다.

```text
agents/financial/tools/monthly_budget.py
```

```python
from typing import Any

from app.agents.base import ToolResult
from app.agents.financial.tools.pending import build_tool_schema

NAME = "analyze_monthly_budget"
SCHEMA = build_tool_schema(NAME, "사용자의 월별 예산 사용 상태를 분석한다.")


def execute(tool_name: str, arguments: dict[str, Any]) -> ToolResult:
    # 실제 백엔드 API 또는 데이터 서비스 연결 코드를 구현한다.
    return ToolResult(
        status="success",
        tool=tool_name,
        data={"budgetStatus": "on_track"},
    )
```

그다음 `tools/registry.py`의 `TOOL_MODULES`에 모듈을 등록한다.

아직 실제 연동이 준비되지 않았다면 다음 형태로 스켈레톤만 추가할 수 있다.

```python
from app.agents.financial.tools.pending import build_tool_schema, pending_result

NAME = "analyze_monthly_budget"
SCHEMA = build_tool_schema(NAME, "사용자의 월별 예산 사용 상태를 분석한다.")
execute = pending_result
```

미구현 Tool은 성공한 것처럼 응답하지 않고 반드시 `pending_integration` 상태를 반환해야 한다.

## 새 Agent 추가 방법

Tool의 목적과 데이터가 기존 금융 Agent와 크게 다르면 별도 Agent로 분리한다.

```text
agents/
└─ goal/
   ├─ __init__.py
   ├─ agent.py
   ├─ prompts.py
   └─ tools/
      ├─ __init__.py
      ├─ registry.py
      ├─ create_goal.py
      └─ create_roadmap.py
```

새 Agent는 다음 책임을 가진다.

1. 도메인 시스템 프롬프트를 관리한다.
2. 자신이 사용할 Tool 목록만 모델에 전달한다.
3. Tool 이름과 실행 함수를 Registry에서 찾는다.
4. Tool 결과를 받아 최종 사용자 답변을 생성한다.

Tool 하나마다 Agent를 만들지 않는다. 서로 연관된 Tool을 도메인 단위로 묶되, 하나의 Agent가
너무 많은 목적과 Tool을 담당하게 되면 별도 Agent로 분리한다.

## 새 API 기능 추가 방법

새 API 기능은 다음 구조를 기본으로 한다.

```text
app/new_feature/
├─ __init__.py
├─ router.py
├─ schemas.py
├─ service.py
├─ prompts.py       # 프롬프트가 있을 때만 추가
└─ repository.py    # 데이터 접근이 있을 때만 추가
```

완성된 Router는 `application.py`에 등록한다.

```python
from app.new_feature.router import router as new_feature_router

app.include_router(new_feature_router)
```

### 카테고리 분류 API

카테고리 분류 기능은 `app/category/` 패키지에서 관리한다. Router가 HTTP 요청을
받고, Service가 Groq 호출과 결과 검증을 수행하며, Pydantic 모델과 프롬프트는 각각
전용 파일에 둔다.

```text
app/category/
├─ __init__.py                # 카테고리 스키마의 호환 import 제공
├─ router.py                  # `/api/category/classify`와 batch 경로
├─ schemas.py                 # 요청·응답 모델과 ExpenseCategory
├─ service.py                 # 단건·배치 분류 흐름, JSON·결과 개수 검증
└─ prompts.py                 # Groq system/user prompt 생성
```

현재 제공하는 API는 다음과 같다.

| Method | Path | 역할 |
| --- | --- | --- |
| `POST` | `/api/category/classify` | 거래 1건을 카테고리로 분류 |
| `POST` | `/api/category/classify/batch` | 거래 여러 건을 입력 순서대로 분류 |

단건 요청 예시는 다음과 같다.

```json
{
  "merchantName": "우리동네 세탁소",
  "merchantSector": "기타",
  "amount": 12000
}
```

응답은 `ExpenseCategory`에 정의된 카테고리 코드와 `0`부터 `1` 사이의
`confidence`를 반환한다.

```json
{
  "category": "LIVING",
  "confidence": 0.9
}
```

카테고리 분류의 처리 흐름은 다음과 같다.

1. `router.py`가 요청 형식과 HTTP 응답을 관리한다.
2. `service.py`가 `clients/groq_client.py`를 통해 Groq를 호출한다.
3. `prompts.py`의 지침과 거래 정보를 모델에 전달한다.
4. Groq 응답을 `schemas.py` 모델로 검증한다.
5. 배치 응답은 입력 건수와 결과 건수가 같은지 추가로 검증한다.

Groq 호출 실패는 다음 기준으로 HTTP 상태 코드로 변환한다.

- `GROQ_API_KEY` 미설정: `503 Service Unavailable`
- Groq API 오류: `502 Bad Gateway`
- JSON 형식, 카테고리 코드 또는 결과 건수 오류: `502 Bad Gateway`

카테고리 API는 `application.py`에서 다음과 같이 등록한다.

```python
from app.category.router import router as category_router

app.include_router(category_router)
```

카테고리 테스트는 실제 Groq API를 호출하지 않고 `create_groq_client`를 Fake Client로
대체한다. 경로, 입력 검증, 정상 응답, 배치 순서, AI 오류 상태 코드를
`tests/test_category.py`에서 확인한다.

### 소비 리포트 생성 API

소비 리포트 생성은 `app/asset_reports/` 패키지에서 관리한다. Spring 백엔드가
선정한 카테고리와 현재·지난달 동일 기간의 집계 금액, 전체 지출액, 현재 월 예산만
전달하며, 거래처명이나 원본 거래내역은 AI 서버로 전달하지 않는다.

| Method | Path | 역할 |
| --- | --- | --- |
| `POST` | `/api/asset-reports/insights/generate` | 카테고리별 집계 금액을 짧은 소비 리포트 문구로 생성 |

요청 예시는 다음과 같다.

```json
{
  "category": "CAFE",
  "categoryLabel": "카페",
  "currentAmount": 300000,
  "previousAmount": 200000,
  "currentTotalAmount": 500000,
  "previousTotalAmount": 600000,
  "monthlyBudget": 700000
}
```

응답은 Spring `AssetReportDto.Insight`의 `reportTitle`·`reportContent`와 호환되는
두 필드만 반환한다. 제목은 15자 이내, 본문은 50자 이내로 검증한다.
`generationMode`는 AI 호출 성공 여부를 알고 있는 Spring 백엔드가 `AI`, `FALLBACK`,
`RULE` 중 하나로 설정한다.

## 테스트 규칙

- 실제 AI API를 호출하지 않고 Mock 또는 Fake Client를 주입한다.
- Router 테스트에서는 경로, 검증 오류, 상태 코드를 확인한다.
- Service 테스트에서는 기능 처리 순서와 예외 처리를 확인한다.
- Agent 테스트에서는 직접 답변과 Tool 선택 경로를 각각 확인한다.
- Tool 테스트에서는 입력 검증과 실제 실행 결과를 확인한다.
- 같은 HTTP 경로가 중복 등록되지 않았는지 확인한다.

AI 핵심 테스트 실행 예시:

```bash
cd ai
python -m pytest tests/test_application.py tests/test_chat.py tests/test_category.py tests/test_financial_report.py
```

## 환경변수

로컬 비밀값은 `ai/.env`에 저장하고 Git에 커밋하지 않는다.

```dotenv
GROQ_API_KEY=본인의_API_KEY
GROQ_MODEL=openai/gpt-oss-20b

GROQ_REPORT_MODEL=openai/gpt-oss-20b
```

새 환경변수를 추가할 때는 직접 `os.getenv()`를 여러 기능에서 호출하지 않고
`core/config.py`에 조회 함수를 만든다.
