"""Application-level protection for outbound AI requests."""

from __future__ import annotations

import math
import threading
from time import monotonic
from typing import Callable


Clock = Callable[[], float]


class ApplicationGuardError(Exception):
    """Base error for an application-side AI request guard rejection.

    This intentionally does not inherit from RuntimeError. Existing route
    handlers use RuntimeError for configuration failures and map those to
    503; guard rejections must reach the shared 429 handler instead.
    """

    error_code = "AI_GUARD_REJECTED"
    retry_after_seconds = 1.0


class ProviderCircuitOpenError(ApplicationGuardError):
    error_code = "AI_PROVIDER_CIRCUIT_OPEN"

    def __init__(self, retry_after_seconds: float):
        self.retry_after_seconds = max(0.0, retry_after_seconds)
        retry_after = max(1, math.ceil(self.retry_after_seconds))
        super().__init__(
            "AI provider circuit breaker is open; "
            f"retry after {retry_after} seconds"
        )


class ApplicationTokenBudgetExceeded(ApplicationGuardError):
    error_code = "AI_TOKEN_BUDGET_EXCEEDED"

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
    error_code = "AI_CONCURRENCY_LIMIT_EXCEEDED"

    def __init__(
        self,
        max_in_flight_requests: int,
        retry_after_seconds: float = 1.0,
    ):
        self.max_in_flight_requests = max_in_flight_requests
        self.retry_after_seconds = max(0.0, retry_after_seconds)
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
        self._configured_capacity = self.capacity
        self._configured_refill_tokens_per_second = self.refill_tokens_per_second
        self._clock = clock
        self._lock = threading.Lock()
        self._tokens = self.capacity
        self._updated_at = self._clock()
        self._provider_limit_tokens: float | None = None
        self._provider_remaining_tokens: float | None = None
        self._provider_reset_at: float | None = None

    def _refill_locked(self, now: float) -> None:
        if self._provider_reset_at is not None and now >= self._provider_reset_at:
            self._provider_remaining_tokens = self.capacity
            self._provider_reset_at = None

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
            now = self._clock()
            self._refill_locked(now)
            available = self._tokens
            if self._provider_remaining_tokens is not None:
                available = min(available, self._provider_remaining_tokens)

            if available >= amount:
                self._tokens -= amount
                if self._provider_remaining_tokens is not None:
                    self._provider_remaining_tokens -= amount
                return None

            local_shortage = max(0.0, amount - self._tokens)
            retry_after = local_shortage / self.refill_tokens_per_second
            if self._provider_remaining_tokens is not None:
                provider_shortage = max(
                    0.0,
                    amount - self._provider_remaining_tokens,
                )
                if provider_shortage > 0:
                    provider_retry_after = max(
                        0.0,
                        (self._provider_reset_at or now) - now,
                    )
                    retry_after = max(retry_after, provider_retry_after)
            return retry_after

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
            if self._provider_remaining_tokens is not None:
                self._provider_remaining_tokens = min(
                    self.capacity,
                    max(0.0, self._provider_remaining_tokens + difference),
                )

    def synchronize_provider_limits(
        self,
        *,
        limit_tokens: int | float,
        remaining_tokens: int | float | None = None,
        reset_seconds: int | float | None = None,
    ) -> bool:
        """Apply provider token-window state as a local safety ceiling."""
        try:
            provider_limit = float(limit_tokens)
            provider_remaining = (
                None if remaining_tokens is None else float(remaining_tokens)
            )
            provider_reset = (
                None if reset_seconds is None else float(reset_seconds)
            )
        except (TypeError, ValueError):
            return False

        if not math.isfinite(provider_limit) or provider_limit <= 0:
            return False
        if provider_remaining is not None and (
            not math.isfinite(provider_remaining) or provider_remaining < 0
        ):
            return False
        if provider_reset is not None and (
            not math.isfinite(provider_reset) or provider_reset < 0
        ):
            return False

        with self._lock:
            now = self._clock()
            self._refill_locked(now)
            effective_capacity = min(self._configured_capacity, provider_limit)
            self.capacity = effective_capacity
            self.refill_tokens_per_second = min(
                self._configured_refill_tokens_per_second,
                provider_limit / 60,
            )
            self._provider_limit_tokens = provider_limit
            self._tokens = min(self._tokens, effective_capacity)

            if provider_remaining is not None:
                self._tokens = min(effective_capacity, provider_remaining)
                if provider_reset is not None and provider_reset > 0:
                    self._provider_remaining_tokens = min(
                        effective_capacity,
                        provider_remaining,
                    )
                    self._provider_reset_at = now + provider_reset
                else:
                    self._provider_remaining_tokens = None
                    self._provider_reset_at = None

            self._updated_at = now
        return True

    @property
    def available_tokens(self) -> float:
        with self._lock:
            self._refill_locked(self._clock())
            if self._provider_remaining_tokens is None:
                return self._tokens
            return min(self._tokens, self._provider_remaining_tokens)


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

    def synchronize_provider_limits(
        self,
        *,
        limit_tokens: int | float,
        remaining_tokens: int | float | None = None,
        reset_seconds: int | float | None = None,
    ) -> bool:
        return self.bucket.synchronize_provider_limits(
            limit_tokens=limit_tokens,
            remaining_tokens=remaining_tokens,
            reset_seconds=reset_seconds,
        )

    @property
    def in_flight_requests(self) -> int:
        with self._in_flight_lock:
            return self._in_flight
