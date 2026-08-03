package com.wallo.pointshop.mapper;

import com.wallo.pointshop.domain.PointShopInventoryItem;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 포인트 잔액과 보관함 상품을 조회하는 MyBatis Mapper임. */
public interface PointShopMapper {

    /** 로그인 사용자의 현재 포인트를 조회함. */
    Integer findPointBalance(@Param("userId") Long userId);

    /** 삭제되지 않은 사용자의 보관함 상품을 최근 획득 순으로 조회함. */
    List<PointShopInventoryItem> findInventoryItems(@Param("userId") Long userId);
}
