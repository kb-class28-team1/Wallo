package com.wallo.chat.mapper;

import com.wallo.chat.dto.ProductRecommendationResultDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ProductRecommendationResultMapper {
    int insert(ProductRecommendationResultDto.SaveCommand command);

    List<ProductRecommendationResultDto.StoredResult> findByAssistantMessageIds(
            @Param("assistantMessageIds") List<Long> assistantMessageIds
    );

    ProductRecommendationResultDto.LatestStoredResult findLatestByUserId(
            @Param("userId") long userId
    );
}
