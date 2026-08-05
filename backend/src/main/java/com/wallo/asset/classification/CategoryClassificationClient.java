package com.wallo.asset.classification;

import java.util.List;
import java.util.stream.Collectors;

public interface CategoryClassificationClient {

    CategoryClassificationDto.Response classify(CategoryClassificationDto.Request request);

    default List<CategoryClassificationDto.Response> classifyBatch(
            List<CategoryClassificationDto.Request> requests
    ) {
        return requests.stream().map(this::classify).collect(Collectors.toList());
    }
}
