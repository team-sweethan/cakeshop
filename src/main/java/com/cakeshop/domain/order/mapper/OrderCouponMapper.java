package com.cakeshop.domain.order.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 이정후
 * 담당자 : 주환
 * 작성일 : 2026-08-07
 * 기능 : 쿠폰 연동용 주문 이력 조회 SQL 계약
 * 설명 : 쿠폰 도메인이 필요한 주문 이력 조회 SQL을 주문 도메인에서 제공한다.
 * ******************************
 */
@Mapper
public interface OrderCouponMapper {

    List<Long> findMemberIdsWithOrderHistory(@Param("memberIds") List<Long> memberIds);

    boolean existsOrderHistory(@Param("memberId") Long memberId);
}
