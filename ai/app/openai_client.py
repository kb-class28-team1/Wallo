"""OpenAI 클라이언트 생성과 모델명 환경변수 조회.

주의: 모듈 import 시점에 전역 OpenAI 클라이언트를 만들지 않는다. 반드시 요청 처리 시점에
create_openai_client()로 만들어야, 테스트에서 실제 SDK 대신 Fake 객체를 대신 넣을 수 있다.
"""

import os

from openai import OpenAI

DEFAULT_MODEL = "gpt-4o-mini"


def get_openai_api_key() -> str | None:
    return os.getenv("OPENAI_API_KEY")


def create_openai_client(api_key: str | None = None) -> OpenAI:
    """OpenAI 클라이언트를 생성한다. api_key를 주지 않으면 SDK가 OPENAI_API_KEY 환경변수를 직접 읽는다."""
    if api_key:
        return OpenAI(api_key=api_key)
    return OpenAI()


def get_chat_model() -> str:
    return os.getenv("OPENAI_MODEL", DEFAULT_MODEL)


def get_report_model() -> str:
    """OPENAI_REPORT_MODEL이 있으면 우선 사용하고, 없으면 채팅과 같은 OPENAI_MODEL을 재사용한다."""
    return os.getenv("OPENAI_REPORT_MODEL") or get_chat_model()
