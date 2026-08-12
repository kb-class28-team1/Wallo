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
            @Param("type") String type,
            @Param("referenceKey") String referenceKey,
            @Param("description") String description);

    /** 같은 주간 랭킹 보상이 이미 지급된 경우 중복 저장하지 않음. */
    int insertWeeklyRankingRewardHistory(
            @Param("userId") Long userId,
            @Param("amount") Integer amount,
            @Param("referenceKey") String referenceKey,
            @Param("description") String description);

    /** 테스트 버튼은 매번 지급되므로 중복 방지 없는 별도 이력으로 저장함. */
    int insertWeeklyRankingTestRewardHistory(
            @Param("userId") Long userId,
            @Param("amount") Integer amount,
            @Param("referenceKey") String referenceKey,
            @Param("description") String description);

    /** 같은 일일 미션에 포인트를 한 번만 지급하도록 이력을 저장함. */
    int insertMissionRewardHistory(
            @Param("userId") Long userId,
            @Param("amount") Integer amount,
            @Param("referenceKey") String referenceKey,
            @Param("description") String description);

    /** 즉시 지급에 당첨된 포인트를 사용자 잔액에 더함. */
    int addPoints(
            @Param("userId") Long userId,
            @Param("amount") Integer amount);

    /** 로그인 사용자가 사용 완료한 상품을 실제로 삭제함. */
    int deleteUsedInventoryItem(
            @Param("userId") Long userId,
            @Param("inventoryId") Long inventoryId);

    /** 당첨 상품을 사용자 보관함에 저장하고 생성된 ID를 채움. */
    int insertInventoryReward(PointShopReward reward);
}
