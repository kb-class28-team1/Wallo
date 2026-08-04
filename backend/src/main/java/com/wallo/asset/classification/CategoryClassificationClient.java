package com.wallo.asset.classification;

public interface CategoryClassificationClient {

    CategoryClassificationDto.Response classify(CategoryClassificationDto.Request request);
}
