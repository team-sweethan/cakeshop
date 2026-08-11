package com.cakeshop.domain.coupon.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.coupon.dto.view.CustomerCouponQueryView;

/**
 * ******************************
 * 작성자 : 수민(이정후)
 * 담당자 : 이정후
 * 작성일 : 2026-08-10
 * 기능 : member 도메인 연동용 쿠폰 SQL 계약
 * 설명 : CouponMemberQueryService의 고객 쿠폰함 조회와 CouponMemberCommandService의
 *       신규 회원 쿠폰 발급에 필요한 쿠폰 소유 SQL을 제공한다.
 * ******************************
 */
@Mapper
public interface CouponMemberMapper {

    /** 고객 마이페이지에 표시할 사용 가능 쿠폰을 페이지 조회한다. */
    List<CustomerCouponQueryView> findMemberCoupons(
            @Param("memberId") long memberId,
            @Param("size") int size,
            @Param("offset") int offset
    );

    /** 고객 마이페이지에 표시할 사용 가능 쿠폰의 전체 건수를 조회한다. */
    long countMemberCoupons(@Param("memberId") long memberId);

    /** 회원가입 시점에 발급 가능한 NEW_MEMBERS 쿠폰 ID를 조회한다. */
    List<Long> findAvailableNewMemberCouponIds();

    /** 활성 일반 회원에게 중복되지 않는 신규 회원 쿠폰 발급 이력을 생성한다. */
    int insertNewMemberCouponIfAbsent(@Param("couponId") long couponId,
                                      @Param("memberId") long memberId);

    /** 신규 회원 쿠폰의 발급 이력 생성 뒤 발급 수량을 조건부로 증가한다. */
    int increaseNewMemberCouponIssuedQuantity(@Param("couponId") long couponId);
}
