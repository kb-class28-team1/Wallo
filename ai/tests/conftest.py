import pytest

from app.core.ai_timing import reset_application_ai_guard


@pytest.fixture(autouse=True)
def reset_ai_guard_between_tests():
    """Reset the process-global token bucket and its in-memory FIFO queue."""
    reset_application_ai_guard()
    try:
        yield
    finally:
        reset_application_ai_guard()
