package com.wallo.challenge.mapper;

import com.wallo.challenge.domain.MyFeed;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 로그인 사용자의 게시물 목록과 전체 개수를 조회하는 MyBatis Mapper임. */
public interface MyFeedMapper {

    /** 정렬, 카테고리와 페이지 조건에 맞는 내 게시물 목록을 조회함. */
    List<MyFeed> findMyFeeds(
            @Param("userId") Long userId,
            @Param("sort") String sort,
            @Param("category") String category,
            @Param("offset") int offset,
            @Param("size") int size);

    /** 선택한 카테고리에 해당하는 내 게시물 전체 개수를 조회함. */
    long countMyFeeds(
            @Param("userId") Long userId,
            @Param("category") String category);
}
