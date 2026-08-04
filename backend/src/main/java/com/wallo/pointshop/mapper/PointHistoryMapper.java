package com.wallo.pointshop.mapper;

import com.wallo.pointshop.dto.response.PointHistoryItemResponse;
import com.wallo.pointshop.dto.response.PointHistorySummaryResponse;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** POINT_HISTORY 테이블을 조회하는 MyBatis Mapper임. */
public interface PointHistoryMapper {

    PointHistorySummaryResponse findSummary(@Param("userId") Long userId);

    List<PointHistoryItemResponse> findItems(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("startDate") String startDate,
            @Param("sort") String sort,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size);

    long countItems(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("startDate") String startDate,
            @Param("keyword") String keyword);
}
