import pytest

from app.core.ai_timing import reset_application_ai_guard


@pytest.fixture(autouse=True)
def reset_ai_guard_between_tests():
    reset_application_ai_guard()
    yield
    reset_application_ai_guard()
