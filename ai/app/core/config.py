import math
import os

from dotenv import load_dotenv


load_dotenv()

DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b"
DEFAULT_GROQ_MAX_RETRIES = 1
MAX_ALLOWED_GROQ_RETRIES = 1
DEFAULT_GROQ_TIMEOUT_SECONDS = 30.0
MIN_ALLOWED_GROQ_TIMEOUT_SECONDS = 1.0
MAX_ALLOWED_GROQ_TIMEOUT_SECONDS = 60.0
DEFAULT_DEMO_ASSET_PROFILE_ID = 7


def get_groq_api_key() -> str | None:
    return os.getenv("GROQ_API_KEY")


def get_groq_model() -> str:
    return os.getenv("GROQ_MODEL", DEFAULT_GROQ_MODEL)


def get_groq_max_retries() -> int:
    raw_retries = os.getenv("GROQ_MAX_RETRIES", str(DEFAULT_GROQ_MAX_RETRIES))
    try:
        max_retries = int(raw_retries)
    except ValueError as error:
        raise RuntimeError("GROQ_MAX_RETRIES는 0 또는 1이어야 합니다.") from error

    if not 0 <= max_retries <= MAX_ALLOWED_GROQ_RETRIES:
        raise RuntimeError("GROQ_MAX_RETRIES는 0 또는 1이어야 합니다.")
    return max_retries


def get_groq_timeout_seconds() -> float:
    raw_timeout = os.getenv(
        "GROQ_TIMEOUT_SECONDS",
        str(DEFAULT_GROQ_TIMEOUT_SECONDS),
    )
    try:
        timeout_seconds = float(raw_timeout)
    except ValueError as error:
        raise RuntimeError("GROQ_TIMEOUT_SECONDS는 숫자여야 합니다.") from error

    if not math.isfinite(timeout_seconds) or not (
        MIN_ALLOWED_GROQ_TIMEOUT_SECONDS
        <= timeout_seconds
        <= MAX_ALLOWED_GROQ_TIMEOUT_SECONDS
    ):
        raise RuntimeError(
            "GROQ_TIMEOUT_SECONDS는 1 이상 60 이하의 숫자여야 합니다."
        )
    return timeout_seconds


def get_demo_asset_profile_id() -> int:
    raw_profile_id = os.getenv(
        "DEMO_ASSET_PROFILE_ID",
        str(DEFAULT_DEMO_ASSET_PROFILE_ID),
    )
    try:
        profile_id = int(raw_profile_id)
    except ValueError as error:
        raise RuntimeError("DEMO_ASSET_PROFILE_ID는 정수여야 합니다.") from error
    if profile_id < 1:
        raise RuntimeError("DEMO_ASSET_PROFILE_ID는 1 이상이어야 합니다.")
    return profile_id
