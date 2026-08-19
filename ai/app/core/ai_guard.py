"""Application-level protection for outbound AI requests."""

from __future__ import annotations

import math
import threading
from time import monotonic
from typing import Callable


Clock = Callable[[], float]


class ApplicationGuardError(RuntimeError):
    """Base error for an application-side AI request guard rejection."""


class ApplicationTokenBudgetExceeded(ApplicationGuardError):
    def __init__(
        self,
        *,
        requested_tokens: int,
        available_tokens: float,
        retry_after_seconds: float,
        capacity_exceeded: bool = False,
    ):
        self.requested_tokens = requested_tokens
        self.available_tokens = max(0.0, available_tokens)
        self.retry_after_seconds = max(0.0, retry_after_seconds)
        self.capacity_exceeded = capacity_exceeded
        if capacity_exceeded:
            message = "AI request token estimate exceeds application bucket capacity"
        else:
            retry_after = max(1, math.ceil(self.retry_after_seconds))
            message = (
                "AI application token budget is temporarily exhausted; "
                f"retry after {retry_after} seconds"
            )
        super().__init__(message)


class ApplicationConcurrencyLimitExceeded(ApplicationGuardError):
    def __init__(self, max_in_flight_requests: int):
        self.max_in_flight_requests = max_in_flight_requests
        super().__init__(
            "AI application concurrency limit reached; please retry shortly"
        )


class TokenBucket:
    """Thread-safe token bucket with an injectable clock for deterministic tests."""

    def __init__(
        self,
        capacity: int,
        refill_tokens_per_second: float,
        *,
        clock: Clock = monotonic,
    ):
        if capacity <= 0:
            raise ValueError("capacity must be positive")
        if refill_tokens_per_second <= 0:
            raise ValueError("refill_tokens_per_second must be positive")
        self.capacity = float(capacity)
        self.refill_tokens_per_second = float(refill_tokens_per_second)
        self._clock = clock
        self._lock = threading.Lock()
        self._tokens = self.capacity
        self._updated_at = self._clock()

    def _refill_locked(self, now: float) -> None:
        elapsed = max(0.0, now - self._updated_at)
        if elapsed:
            self._tokens = min(
                self.capacity,
                self._tokens + elapsed * self.refill_tokens_per_second,
            )
            self._updated_at = now

    def try_consume(self, tokens: int | float) -> float | None:
        amount = max(0.0, float(tokens))
        if amount == 0:
            return None
        with self._lock:
            self._refill_locked(self._clock())
            if self._tokens >= amount:
                self._tokens -= amount
                return None
            shortage = amount - self._tokens
            return shortage / self.refill_tokens_per_second

    def reconcile(self, reserved_tokens: int | float, actual_tokens: int | None) -> None:
        reserved = max(0.0, float(reserved_tokens))
        actual = reserved if actual_tokens is None else max(0.0, float(actual_tokens))
        difference = reserved - actual
        with self._lock:
            self._refill_locked(self._clock())
            if difference >= 0:
                self._tokens = min(self.capacity, self._tokens + difference)
            else:
                self._tokens = max(0.0, self._tokens + difference)

    @property
    def available_tokens(self) -> float:
        with self._lock:
            self._refill_locked(self._clock())
            return self._tokens


class ApplicationGuardLease:
    def __init__(self, guard: "ApplicationAIGuard", reserved_tokens: int):
        self._guard = guard
        self.reserved_tokens = reserved_tokens
        self._released = False

    def release(self, actual_tokens: int | None = None) -> None:
        if self._released:
            return
        self._released = True
        self._guard._release(self.reserved_tokens, actual_tokens)

    @property
    def available_tokens(self) -> float:
        return self._guard.bucket.available_tokens

    @property
    def in_flight_requests(self) -> int:
        return self._guard.in_flight_requests


class ApplicationAIGuard:
    """Combines an application token budget with an in-flight request limit."""

    def __init__(
        self,
        *,
        token_capacity: int,
        refill_tokens_per_minute: int,
        max_in_flight_requests: int,
        clock: Clock = monotonic,
    ):
        if max_in_flight_requests <= 0:
            raise ValueError("max_in_flight_requests must be positive")
        self.bucket = TokenBucket(
            token_capacity,
            refill_tokens_per_minute / 60,
            clock=clock,
        )
        self.max_in_flight_requests = max_in_flight_requests
        self._in_flight = 0
        self._in_flight_lock = threading.Lock()
        self._semaphore = threading.BoundedSemaphore(max_in_flight_requests)

    def reserve(self, requested_tokens: int) -> ApplicationGuardLease:
        if requested_tokens > self.bucket.capacity:
            raise ApplicationTokenBudgetExceeded(
                requested_tokens=requested_tokens,
                available_tokens=self.bucket.available_tokens,
                retry_after_seconds=0,
                capacity_exceeded=True,
            )
        retry_after = self.bucket.try_consume(requested_tokens)
        if retry_after is not None:
            raise ApplicationTokenBudgetExceeded(
                requested_tokens=requested_tokens,
                available_tokens=self.bucket.available_tokens,
                retry_after_seconds=retry_after,
            )
        if not self._semaphore.acquire(blocking=False):
            self.bucket.reconcile(requested_tokens, 0)
            raise ApplicationConcurrencyLimitExceeded(self.max_in_flight_requests)
        with self._in_flight_lock:
            self._in_flight += 1
        return ApplicationGuardLease(self, requested_tokens)

    def _release(self, reserved_tokens: int, actual_tokens: int | None) -> None:
        self.bucket.reconcile(reserved_tokens, actual_tokens)
        with self._in_flight_lock:
            self._in_flight = max(0, self._in_flight - 1)
        self._semaphore.release()

    @property
    def in_flight_requests(self) -> int:
        with self._in_flight_lock:
            return self._in_flight
