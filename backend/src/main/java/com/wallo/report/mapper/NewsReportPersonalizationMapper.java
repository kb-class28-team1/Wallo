package com.wallo.report.mapper;

import com.wallo.report.domain.NewsReportPersonalization;
import com.wallo.report.domain.ReportUserProfile;
import org.apache.ibatis.annotations.Param;

public interface NewsReportPersonalizationMapper {
    NewsReportPersonalization findByNewsIdAndUserId(@Param("newsId") Long newsId, @Param("userId") Long userId);
    int insert(NewsReportPersonalization personalization);
    ReportUserProfile findUserProfile(@Param("userId") Long userId);
}
