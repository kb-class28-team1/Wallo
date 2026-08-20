import math
import os

from dotenv import load_dotenv


load_dotenv()

DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b"
DEFAULT_GROQ_MAX_RETRIES = 1
MAX_ALLOWED_GROQ_RETRIES = 1
DEFAULT_GROQ_MAX_RETRY_DELAY_SECONDS = 5.0
MIN_ALLOWED_GROQ_MAX_RETRY_DELAY_SECONDS = 1.0
MAX_ALLOWED_GROQ_MAX_RETRY_DELAY_SECONDS = 10.0
DEFAULT_GROQ_CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3
MIN_ALLOWED_GROQ_CIRCUIT_BREAKER_FAILURE_THRESHOLD = 2
MAX_ALLOWED_GROQ_CIRCUIT_BREAKER_FAILURE_THRESHOLD = 10
DEFAULT_GROQ_CIRCUIT_BREAKER_WINDOW_SECONDS = 30.0
MIN_ALLOWED_GROQ_CIRCUIT_BREAKER_WINDOW_SECONDS = 1.0
MAX_ALLOWED_GROQ_CIRCUIT_BREAKER_WINDOW_SECONDS = 300.0
DEFAULT_GROQ_CIRCUIT_BREAKER_OPEN_SECONDS = 30.0
MIN_ALLOWED_GROQ_CIRCUIT_BREAKER_OPEN_SECONDS = 1.0
MAX_ALLOWED_GROQ_CIRCUIT_BREAKER_OPEN_SECONDS = 600.0
DEFAULT_GROQ_TIMEOUT_SECONDS = 30.0
MIN_ALLOWED_GROQ_TIMEOUT_SECONDS = 1.0
MAX_ALLOWED_GROQ_TIMEOUT_SECONDS = 60.0
DEFAULT_AI_TOKEN_BUCKET_CAPACITY = 20_000
MIN_ALLOWED_AI_TOKEN_BUCKET_CAPACITY = 1_000
MAX_ALLOWED_AI_TOKEN_BUCKET_CAPACITY = 100_000
DEFAULT_AI_TOKEN_BUCKET_REFILL_PER_MINUTE = 12_000
MIN_ALLOWED_AI_TOKEN_BUCKET_REFILL_PER_MINUTE = 100
MAX_ALLOWED_AI_TOKEN_BUCKET_REFILL_PER_MINUTE = 100_000
DEFAULT_AI_MAX_IN_FLIGHT_REQUESTS = 4
MIN_ALLOWED_AI_MAX_IN_FLIGHT_REQUESTS = 1
MAX_ALLOWED_AI_MAX_IN_FLIGHT_REQUESTS = 16
DEFAULT_AI_QUEUE_ENABLED = True
DEFAULT_AI_QUEUE_MAX_SIZE = 8
MIN_ALLOWED_AI_QUEUE_MAX_SIZE = 1
MAX_ALLOWED_AI_QUEUE_MAX_SIZE = 100
DEFAULT_AI_QUEUE_MAX_WAIT_SECONDS = 30.0
MIN_ALLOWED_AI_QUEUE_MAX_WAIT_SECONDS = 1.0
MAX_ALLOWED_AI_QUEUE_MAX_WAIT_SECONDS = 300.0


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


def get_groq_max_retry_delay_seconds() -> float:
    raw_delay = os.getenv(
        "GROQ_MAX_RETRY_DELAY_SECONDS",
        str(DEFAULT_GROQ_MAX_RETRY_DELAY_SECONDS),
    )
    try:
        delay_seconds = float(raw_delay)
    except ValueError as error:
        raise RuntimeError(
            "GROQ_MAX_RETRY_DELAY_SECONDS는 숫자여야 합니다."
        ) from error

    if not math.isfinite(delay_seconds) or not (
        MIN_ALLOWED_GROQ_MAX_RETRY_DELAY_SECONDS
        <= delay_seconds
        <= MAX_ALLOWED_GROQ_MAX_RETRY_DELAY_SECONDS
    ):
        raise RuntimeError(
            "GROQ_MAX_RETRY_DELAY_SECONDS는 1 이상 10 이하의 숫자여야 합니다."
        )
    return delay_seconds


def get_groq_circuit_breaker_failure_threshold() -> int:
    return _get_bounded_int(
        "GROQ_CIRCUIT_BREAKER_FAILURE_THRESHOLD",
        DEFAULT_GROQ_CIRCUIT_BREAKER_FAILURE_THRESHOLD,
        MIN_ALLOWED_GROQ_CIRCUIT_BREAKER_FAILURE_THRESHOLD,
        MAX_ALLOWED_GROQ_CIRCUIT_BREAKER_FAILURE_THRESHOLD,
    )


def _get_bounded_float(
    variable_name: str,
    default: float,
    minimum: float,
    maximum: float,
) -> float:
    raw_value = os.getenv(variable_name, str(default))
    try:
        value = float(raw_value)
    except ValueError as error:
        raise RuntimeError(f"{variable_name} must be a number") from error
    if not math.isfinite(value) or not minimum <= value <= maximum:
        raise RuntimeError(
            f"{variable_name} must be between {minimum} and {maximum}"
        )
    return value


def get_groq_circuit_breaker_window_seconds() -> float:
    return _get_bounded_float(
        "GROQ_CIRCUIT_BREAKER_WINDOW_SECONDS",
        DEFAULT_GROQ_CIRCUIT_BREAKER_WINDOW_SECONDS,
        MIN_ALLOWED_GROQ_CIRCUIT_BREAKER_WINDOW_SECONDS,
        MAX_ALLOWED_GROQ_CIRCUIT_BREAKER_WINDOW_SECONDS,
    )


def get_groq_circuit_breaker_open_seconds() -> float:
    return _get_bounded_float(
        "GROQ_CIRCUIT_BREAKER_OPEN_SECONDS",
        DEFAULT_GROQ_CIRCUIT_BREAKER_OPEN_SECONDS,
        MIN_ALLOWED_GROQ_CIRCUIT_BREAKER_OPEN_SECONDS,
        MAX_ALLOWED_GROQ_CIRCUIT_BREAKER_OPEN_SECONDS,
    )


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


def _get_bounded_int(
    variable_name: str,
    default: int,
    minimum: int,
    maximum: int,
) -> int:
    raw_value = os.getenv(variable_name, str(default))
    try:
        value = int(raw_value)
    except ValueError as error:
        raise RuntimeError(f"{variable_name} must be an integer") from error
    if not minimum <= value <= maximum:
        raise RuntimeError(
            f"{variable_name} must be between {minimum} and {maximum}"
        )
    return value


def _get_bool(variable_name: str, default: bool) -> bool:
    raw_value = os.getenv(variable_name, str(default).lower()).strip().lower()
    if raw_value in {"1", "true", "yes", "on"}:
        return True
    if raw_value in {"0", "false", "no", "off"}:
        return False
    raise RuntimeError(f"{variable_name} must be a boolean")


def get_ai_token_bucket_capacity() -> int:
    return _get_bounded_int(
        "AI_TOKEN_BUCKET_CAPACITY",
        DEFAULT_AI_TOKEN_BUCKET_CAPACITY,
        MIN_ALLOWED_AI_TOKEN_BUCKET_CAPACITY,
        MAX_ALLOWED_AI_TOKEN_BUCKET_CAPACITY,
    )


def get_ai_token_bucket_refill_per_minute() -> int:
    return _get_bounded_int(
        "AI_TOKEN_BUCKET_REFILL_PER_MINUTE",
        DEFAULT_AI_TOKEN_BUCKET_REFILL_PER_MINUTE,
        MIN_ALLOWED_AI_TOKEN_BUCKET_REFILL_PER_MINUTE,
        MAX_ALLOWED_AI_TOKEN_BUCKET_REFILL_PER_MINUTE,
    )


def get_ai_max_in_flight_requests() -> int:
    return _get_bounded_int(
        "AI_MAX_IN_FLIGHT_REQUESTS",
        DEFAULT_AI_MAX_IN_FLIGHT_REQUESTS,
        MIN_ALLOWED_AI_MAX_IN_FLIGHT_REQUESTS,
        MAX_ALLOWED_AI_MAX_IN_FLIGHT_REQUESTS,
    )


def get_ai_queue_enabled() -> bool:
    return _get_bool("AI_QUEUE_ENABLED", DEFAULT_AI_QUEUE_ENABLED)


def get_ai_queue_max_size() -> int:
    return _get_bounded_int(
        "AI_QUEUE_MAX_SIZE",
        DEFAULT_AI_QUEUE_MAX_SIZE,
        MIN_ALLOWED_AI_QUEUE_MAX_SIZE,
        MAX_ALLOWED_AI_QUEUE_MAX_SIZE,
    )


def get_ai_queue_max_wait_seconds() -> float:
    return _get_bounded_float(
        "AI_QUEUE_MAX_WAIT_SECONDS",
        DEFAULT_AI_QUEUE_MAX_WAIT_SECONDS,
        MIN_ALLOWED_AI_QUEUE_MAX_WAIT_SECONDS,
        MAX_ALLOWED_AI_QUEUE_MAX_WAIT_SECONDS,
    )
