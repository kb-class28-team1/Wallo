import os

from dotenv import load_dotenv


load_dotenv()

DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b"


def get_groq_api_key() -> str | None:
    return os.getenv("GROQ_API_KEY")


def get_groq_model() -> str:
    return os.getenv("GROQ_MODEL", DEFAULT_GROQ_MODEL)
