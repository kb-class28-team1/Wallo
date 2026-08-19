import pytest

from app.core.ai_guard import TokenBucket


def test_token_bucket_synchronizes_provider_limit_remaining_and_reset():
    now = [0.0]
    bucket = TokenBucket(
        capacity=20_000,
        refill_tokens_per_second=200,
        clock=lambda: now[0],
    )

    assert bucket.synchronize_provider_limits(
        limit_tokens=8_000,
        remaining_tokens=1_959,
        reset_seconds=3,
    ) is True
    assert bucket.capacity == 8_000
    assert bucket.refill_tokens_per_second == pytest.approx(133.333333, abs=0.001)
    assert bucket.available_tokens == pytest.approx(1_959)
    assert bucket.try_consume(2_000) == pytest.approx(3.0)

    now[0] = 3.0
    assert bucket.try_consume(2_000) is None


def test_provider_limit_cannot_raise_configured_application_capacity():
    bucket = TokenBucket(
        capacity=8_000,
        refill_tokens_per_second=100,
    )

    assert bucket.synchronize_provider_limits(
        limit_tokens=20_000,
        remaining_tokens=15_000,
        reset_seconds=10,
    ) is True
    assert bucket.capacity == 8_000
    assert bucket.available_tokens == pytest.approx(8_000)
    assert bucket.refill_tokens_per_second == pytest.approx(100)


def test_invalid_provider_limits_are_ignored():
    bucket = TokenBucket(
        capacity=8_000,
        refill_tokens_per_second=100,
    )

    assert bucket.synchronize_provider_limits(
        limit_tokens="not-a-number",
        remaining_tokens=100,
        reset_seconds=1,
    ) is False
    assert bucket.capacity == 8_000
    assert bucket.available_tokens == pytest.approx(8_000)
