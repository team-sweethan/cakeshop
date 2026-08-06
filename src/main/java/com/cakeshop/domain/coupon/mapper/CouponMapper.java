package com.cakeshop.domain.coupon.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.coupon.dto.form.CouponSearchCondition;
import com.cakeshop.domain.coupon.dto.view.CouponView;
import com.cakeshop.domain.coupon.dto.view.CouponIssuedMemberView;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.CouponTargetType;

/**
 * 쿠폰 도메인의 SQL 경계다.
 * 구현 SQL은 CouponMapper.xml에 두며, 서비스는 이 인터페이스만 의존한다.
 */
@Mapper
public interface CouponMapper {

    /** 관리자 등록 화면에서 입력한 쿠폰 한 건을 저장한다. */
    int insertCoupon(Coupon coupon);

    /** 수정·상태 전이 전에 현재 DB 상태를 확인한다. */
    Optional<Coupon> findCouponById( @Param("couponId") Long couponId );
    Optional<Coupon> findCouponByIdForUpdate(@Param("couponId") Long couponId);

    /** 검색 조건과 LIMIT/OFFSET에 해당하는 목록 화면용 행을 조회한다. */
    List<CouponView> findCoupons( @Param("condition") CouponSearchCondition condition,
                                  @Param("size") int size,
                                  @Param("offset") int offset
    );

    /** 목록 쿼리와 동일한 검색 조건의 전체 건수를 조회한다. */
    long countCoupons( @Param("condition") CouponSearchCondition condition );

    /** 시작 전 쿠폰의 모든 관리 가능 필드를 수정하고, 수정 시점에도 시작 전 상태인지 확인한다. */
    int updateCouponBeforeStart(Coupon coupon);

    /** 시작 후 쿠폰의 허용 필드만 수정하고, 발급 수량·만료 시각 조건을 SQL에서도 다시 확인한다. */
    int updateCouponAfterStart(Coupon coupon);

    /** 관리자 명령에 따라 ACTIVE와 INACTIVE 상태를 전환한다. */
    int updateStatus( @Param("couponId") Long couponId, @Param("status") CouponStatus status );

    /** 발급 시 회원 상태를 확인하고, 같은 쿠폰의 중복 발급은 무시한다. */
    int insertMemberCouponIfAbsent(@Param("couponId") Long couponId,
                                   @Param("memberId") Long memberId,
                                   @Param("allowBeforeStart") boolean allowBeforeStart,
                                   @Param("firstOrderOnly") boolean firstOrderOnly);
    int increaseIssuedQuantityIfAvailable(@Param("couponId") Long couponId);
    int deleteAvailableMemberCoupon(@Param("couponId") Long couponId, @Param("memberId") Long memberId);
    int decreaseIssuedQuantity(@Param("couponId") Long couponId);
    /** 회원 조회 결과 중 이미 발급된 회원을 표시하기 위한 식별자 목록이다. */
    List<Long> findIssuedMemberIds(@Param("couponId") Long couponId, @Param("memberIds") List<Long> memberIds);
    List<CouponIssuedMemberView> findIssuedMembers(@Param("couponId") Long couponId, @Param("keyword") String keyword, @Param("size") int size, @Param("offset") int offset);
    long countIssuedMembers(@Param("couponId") Long couponId, @Param("keyword") String keyword);
    /** 대상 정책별로 발급 후보 쿠폰을 조회한다. */
    List<Coupon> findCouponsByTargetType(@Param("targetType") CouponTargetType targetType);

}
