from app.financial_assistant.asset_analysis_cache import build_cache_key


def _database_tool_data(*, as_of: str, total_assets: int = 100_000_000) -> dict:
    return {
        "dataMode": "database",
        "calculatedMetrics": {
            "totalAssetsKrw": total_assets,
            "netAssetsKrw": total_assets,
        },
        "profile": {
            "as_of": as_of,
            "assets": {"total_assets_krw": total_assets},
        },
    }


def test_database_cache_key_is_stable_for_the_same_snapshot():
    data = _database_tool_data(as_of="2026-08-20T10:00:00Z")

    assert build_cache_key(data, "gpt-test") == build_cache_key(data.copy(), "gpt-test")


def test_database_cache_key_changes_when_as_of_changes():
    first_key = build_cache_key(
        _database_tool_data(as_of="2026-08-20T10:00:00Z"),
        "gpt-test",
    )
    second_key = build_cache_key(
        _database_tool_data(as_of="2026-08-20T11:00:00Z"),
        "gpt-test",
    )

    assert first_key is not None
    assert second_key is not None
    assert first_key != second_key


def test_database_cache_key_changes_when_data_changes_with_same_as_of():
    first_key = build_cache_key(
        _database_tool_data(as_of="2026-08-20T10:00:00Z", total_assets=100_000_000),
        "gpt-test",
    )
    second_key = build_cache_key(
        _database_tool_data(as_of="2026-08-20T10:00:00Z", total_assets=101_000_000),
        "gpt-test",
    )

    assert first_key is not None
    assert second_key is not None
    assert first_key != second_key


def test_non_database_payload_is_not_cached():
    assert (
        build_cache_key(
            {
                "dataMode": "legacy",
                "calculatedMetrics": {},
                "profile": {},
            },
            "gpt-test",
        )
        is None
    )
