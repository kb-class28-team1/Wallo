from types import SimpleNamespace
from unittest.mock import Mock

from app.financial_assistant.agent import FinancialAgent
from app.financial_assistant.history import (
    build_bounded_context,
    estimate_message_tokens,
    estimate_text_tokens,
    select_recent_history,
)


def _completion(content):
    return SimpleNamespace(
        choices=[SimpleNamespace(message=SimpleNamespace(content=content, tool_calls=None))]
    )


def test_select_recent_history_keeps_recent_contiguous_messages():
    history = [
        {"role": "user", "content": "old " * 100},
        {"role": "assistant", "content": "recent answer"},
        {"role": "user", "content": "recent question"},
    ]
    budget = sum(estimate_message_tokens(message) for message in history[1:])

    selected, content_truncated = select_recent_history(history, budget)

    assert selected == history[1:]
    assert not content_truncated
    assert sum(estimate_message_tokens(message) for message in selected) <= budget


def test_select_recent_history_truncates_one_oversized_recent_message():
    selected, content_truncated = select_recent_history(
        [{"role": "user", "content": "가" * 100}],
        token_budget=20,
    )

    assert content_truncated
    assert "earlier context omitted" in selected[0]["content"]
    assert estimate_message_tokens(selected[0]) <= 20


def test_build_bounded_context_limits_history_and_summary():
    history = [
        {"role": "user", "content": "old " * 1000},
        {"role": "assistant", "content": "latest"},
    ]
    summary = "요약 " * 1000

    context = build_bounded_context(
        history,
        summary,
        history_token_budget=20,
        summary_token_budget=20,
    )

    assert context.source_history_count == 2
    assert context.history_dropped_count == 1
    assert context.history_estimated_tokens <= 20
    assert context.summary_estimated_tokens <= 20
    assert context.summary_truncated


def test_token_estimate_is_zero_for_empty_text_and_positive_for_content():
    assert estimate_text_tokens("") == 0
    assert estimate_text_tokens("financial context") > 0


def test_financial_agent_sends_bounded_history_to_route_call():
    client = Mock()
    client.chat.completions.create.return_value = _completion("direct answer")
    history = [
        {"role": "user", "content": "old " * 3000},
        {"role": "assistant", "content": "latest answer"},
    ]

    FinancialAgent(client, model="test-model").run(
        "hello",
        history=history,
        summary="summary " * 3000,
    )

    messages = client.chat.completions.create.call_args.kwargs["messages"]
    message_contents = [message.get("content", "") for message in messages]
    assert "old " * 3000 not in message_contents
    assert "latest answer" in message_contents
    summary_messages = [
        content for content in message_contents
        if isinstance(content, str) and "earlier context omitted" in content
    ]
    assert summary_messages
