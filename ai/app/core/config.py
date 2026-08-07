import os

from dotenv import load_dotenv


load_dotenv()

DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b"
DEFAULT_DEMO_ASSET_PROFILE_ID = 3


def get_groq_api_key() -> str | None:
    return os.getenv("GROQ_API_KEY")


def get_groq_model() -> str:
    return os.getenv("GROQ_MODEL", DEFAULT_GROQ_MODEL)


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
