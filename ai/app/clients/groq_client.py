from groq import Groq

from app.core.config import get_groq_api_key


def create_groq_client(api_key: str | None = None) -> Groq:
    resolved_key = api_key or get_groq_api_key()
    if not resolved_key:
        raise RuntimeError("GROQ_API_KEY가 설정되지 않았습니다.")
    return Groq(api_key=resolved_key)
