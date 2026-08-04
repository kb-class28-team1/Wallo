package com.wallo.asset.classification;

import java.math.BigDecimal;

public final class CategoryClassificationDto {

    private CategoryClassificationDto() {
    }

    public record Request(
            String merchantName,
            String merchantSector,
            long amount
    ) {
    }

    public record Response(
            String category,
            BigDecimal confidence
    ) {
    }
}
