package com.cakeshop.domain.coupon.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.coupon.dto.view.CustomerCouponQueryView;

/** 쿠폰 도메인이 소유한 회원 쿠폰 발급 이력의 고객 조회 SQL 계약이다. */
@Mapper
public interface CouponMemberMapper {

    List<CustomerCouponQueryView> findMemberCoupons(
            @Param("memberId") long memberId,
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countMemberCoupons(@Param("memberId") long memberId);
}
