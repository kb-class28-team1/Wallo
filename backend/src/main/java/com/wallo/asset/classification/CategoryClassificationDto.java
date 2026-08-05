package com.wallo.asset.classification;

import java.math.BigDecimal;
import java.util.List;

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

    public record BatchRequest(
            List<Request> items
    ) {
    }

    public record BatchResponse(
            List<Response> results
    ) {
    }
}
