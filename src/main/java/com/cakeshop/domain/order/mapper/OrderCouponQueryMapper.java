package com.cakeshop.domain.order.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 첫 주문 쿠폰 선별을 위한 주문 이력 조회 SQL 계약이다.
 *
 * <p>작성자: 이정후, 주문 담당자 협의 - 쿠폰 도메인이 주문 Mapper를 직접 참조하지 않도록 분리한다.</p>
 */
@Mapper
public interface OrderCouponQueryMapper {

    List<Long> findMemberIdsWithOrderHistory(@Param("memberIds") List<Long> memberIds);

    boolean existsOrderHistory(@Param("memberId") Long memberId);
}
