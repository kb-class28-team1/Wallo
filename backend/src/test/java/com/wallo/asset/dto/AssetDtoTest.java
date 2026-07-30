package com.wallo.asset.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AssetDtoTest {

    @Test
    void responseSortsAssetTrendFromPastToPresent() {
        AssetDto.Response response = new AssetDto.Response(
                39_900_000L,
                38_400_000L,
                null,
                null,
                null,
                null,
                Arrays.asList(
                        new AssetDto.AssetTrend("2026-07", 39_900_000L),
                        new AssetDto.AssetTrend("2026-05", 37_800_000L),
                        new AssetDto.AssetTrend("2026-06", 38_400_000L)
                )
        );

        assertEquals(
                Arrays.asList("2026-05", "2026-06", "2026-07"),
                response.getAssetTrend().stream().map(AssetDto.AssetTrend::getMonth).toList()
        );
    }
}
