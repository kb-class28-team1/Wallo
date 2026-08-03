package com.wallo.pointshop.mapper;

import com.wallo.pointshop.domain.PointShopInventoryItem;
import com.wallo.pointshop.domain.PointShopReward;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 포인트 잔액과 보관함 상품을 조회하는 MyBatis Mapper임. */
public interface PointShopMapper {

    /** 로그인 사용자의 현재 포인트를 조회함. */
    Integer findPointBalance(@Param("userId") Long userId);

    /** 삭제되지 않은 사용자의 보관함 상품을 최근 획득 순으로 조회함. */
    List<PointShopInventoryItem> findInventoryItems(@Param("userId") Long userId);

    /** 포인트가 충분할 때만 상자 가격을 차감함. */
    int deductPoints(
            @Param("userId") Long userId,
            @Param("price") Integer price);

    /** 랜덤박스 사용 포인트 이력을 저장함. */
    int insertPointHistory(
            @Param("userId") Long userId,
            @Param("amount") Integer amount,
            @Param("referenceKey") String referenceKey,
            @Param("description") String description);

    /** 당첨 상품을 사용자 보관함에 저장하고 생성된 ID를 채움. */
    int insertInventoryReward(PointShopReward reward);
}
