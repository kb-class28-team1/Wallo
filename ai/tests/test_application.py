"""POST /api/reports/generate 엔드포인트 레벨 테스트. 실제 OpenAI API를 호출하지 않는다.

실행: ai/ 디렉터리에서 `py -m pytest`
"""

from fastapi.testclient import TestClient

from app.application import app

client = TestClient(app)

_REQUEST_BODY = {
    "newsId": 1,
    "title": "테스트 기사 제목",
    "content": "정상적인 기사 본문입니다.",
    "category": "경제",
    "source": "매일경제",
    "publishedAt": "2026-07-31T09:00:00",
}


def test_returns_503_when_openai_api_key_is_missing_and_mock_disabled(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("AI_REPORT_MOCK_ENABLED", raising=False)

    response = client.post("/api/reports/generate", json=_REQUEST_BODY)

    assert response.status_code == 503


def test_returns_mock_response_when_mock_mode_enabled(monkeypatch):
    monkeypatch.setenv("AI_REPORT_MOCK_ENABLED", "true")
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)  # 키가 없어도 mock 모드면 호출조차 안 한다

    response = client.post("/api/reports/generate", json=_REQUEST_BODY)

    assert response.status_code == 200
    body = response.json()
    assert "[MOCK]" in body["summary"]
