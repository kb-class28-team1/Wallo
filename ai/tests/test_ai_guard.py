import threading
import time

import pytest

from app.core.ai_guard import (
    ApplicationAIGuard,
    ApplicationQueueFullError,
    ApplicationQueueTimeoutError,
    TokenBucket,
)


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


def _wait_until(predicate, timeout=1.0):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return True
        time.sleep(0.01)
    return predicate()


def test_application_guard_queues_requests_in_fifo_order():
    guard = ApplicationAIGuard(
        token_capacity=100,
        refill_tokens_per_minute=6_000,
        max_in_flight_requests=1,
        queue_enabled=True,
        queue_max_size=2,
        queue_max_wait_seconds=1.0,
    )
    active_lease = guard.reserve(100)
    order = []
    acquired = {
        "first": threading.Event(),
        "second": threading.Event(),
    }
    release = {
        "first": threading.Event(),
        "second": threading.Event(),
    }

    def worker(name):
        lease = guard.reserve(1)
        order.append(name)
        acquired[name].set()
        release[name].wait(1.0)
        lease.release(actual_tokens=0)

    first = threading.Thread(target=worker, args=("first",))
    first.start()
    assert _wait_until(lambda: guard.waiting_requests == 1)

    second = threading.Thread(target=worker, args=("second",))
    second.start()
    assert _wait_until(lambda: guard.waiting_requests == 2)

    active_lease.release(actual_tokens=0)
    assert acquired["first"].wait(1.0)
    assert order == ["first"]

    release["first"].set()
    assert acquired["second"].wait(1.0)
    assert order == ["first", "second"]
    release["second"].set()
    first.join(timeout=1.0)
    second.join(timeout=1.0)
    assert not first.is_alive()
    assert not second.is_alive()


def test_application_guard_queue_rejects_when_full():
    guard = ApplicationAIGuard(
        token_capacity=100,
        refill_tokens_per_minute=6_000,
        max_in_flight_requests=1,
        queue_enabled=True,
        queue_max_size=1,
        queue_max_wait_seconds=1.0,
    )
    active_lease = guard.reserve(100)
    result = {}

    def worker():
        try:
            result["lease"] = guard.reserve(1)
        except Exception as error:  # pragma: no cover - defensive thread capture
            result["error"] = error

    queued = threading.Thread(target=worker)
    queued.start()
    assert _wait_until(lambda: guard.waiting_requests == 1)

    with pytest.raises(ApplicationQueueFullError):
        guard.reserve(1)

    active_lease.release(actual_tokens=0)
    queued.join(timeout=1.0)
    assert not queued.is_alive()
    assert "lease" in result
    result["lease"].release(actual_tokens=0)


def test_application_guard_queue_times_out():
    guard = ApplicationAIGuard(
        token_capacity=100,
        refill_tokens_per_minute=6_000,
        max_in_flight_requests=1,
        queue_enabled=True,
        queue_max_size=1,
        queue_max_wait_seconds=0.05,
    )
    active_lease = guard.reserve(100)

    with pytest.raises(ApplicationQueueTimeoutError) as error_info:
        guard.reserve(1)

    assert error_info.value.waited_seconds >= 0.04
    active_lease.release(actual_tokens=0)
