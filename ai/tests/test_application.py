"""application.py 구조 테스트: 앱 생성과 router 등록을 확인한다. 실제 OpenAI 호출은 하지 않는다.

app.routes를 직접 순회하는 대신 app.openapi()로 실제 노출되는 경로를 확인한다 — include_router로
등록된 하위 라우터는 Starlette/FastAPI 내부 표현상 app.routes에 곧바로 펼쳐지지 않기 때문에,
실제로 서비스되는 경로 목록(OpenAPI 스키마)을 기준으로 검증하는 편이 더 안정적이다.
"""

from fastapi import FastAPI

from app.application import app


def _registered_paths_with_method(method: str) -> set[str]:
    schema = app.openapi()
    return {
        path
        for path, operations in schema.get("paths", {}).items()
        if method.lower() in operations
    }


def test_app_is_a_fastapi_instance():
    assert isinstance(app, FastAPI)


def test_chat_endpoint_is_registered():
    assert "/api/chat" in _registered_paths_with_method("POST")


def test_financial_report_generate_endpoint_is_registered():
    assert "/api/reports/generate" in _registered_paths_with_method("POST")


def test_health_endpoint_is_registered():
    assert "/api/health" in _registered_paths_with_method("GET")


def test_no_duplicated_api_prefix_in_registered_paths():
    """router prefix가 중복 등록되어 /api/api/...가 되지 않았는지 확인한다."""
    schema = app.openapi()
    for path in schema.get("paths", {}):
        assert "/api/api" not in path
