package com.cakeshop.domain.coupon.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.cakeshop.domain.coupon.dto.view.CouponOrderDiscount;
import com.cakeshop.domain.coupon.dto.view.CouponOrderAvailableView;
import java.util.List;
import java.util.Optional;

/**
 * ******************************
 * 작성자 : 주환(이정후)
 * 담당자 : 이정후
 * 작성일 : 2026-08-10
 * 기능 : order·payment 도메인 연동용 쿠폰 SQL 계약
 * 설명 : 주문서 쿠폰 조회와 주문·결제 흐름의 예약·사용 완료·복구에 필요한
 *       쿠폰 소유 테이블 SQL을 제공한다.
 * ******************************
 */
@Mapper
public interface CouponOrderMapper {

    /** 주문서에 노출할 현재 주문 금액 충족 쿠폰을 조회한다. */
    List<CouponOrderAvailableView> findAvailableCouponsForMember(
            @Param("memberId") long memberId,
            @Param("orderAmount") java.math.BigDecimal orderAmount
    );

    /** 미리보기에서 선택 쿠폰의 현재 유효성과 할인 조건을 다시 조회한다. */
    Optional<CouponOrderDiscount> findAvailableCouponForOrder(
            @Param("memberCouponId") long memberCouponId,
            @Param("memberId") long memberId
    );

    /** 주문 생성 트랜잭션에서 회원 쿠폰 행을 잠근 뒤 예약 가능 여부를 조회한다. */
    Optional<CouponOrderDiscount> findAvailableCouponForOrderForUpdate(
            @Param("memberCouponId") long memberCouponId,
            @Param("memberId") long memberId
    );

    /** 사용 가능 쿠폰을 결제 대기 주문에 예약한다. */
    int reserveCouponForOrder(@Param("memberCouponId") long memberCouponId, @Param("orderId") long orderId);

    /** 결제 완료 주문의 예약 쿠폰을 사용 완료로 확정한다. */
    int useReservedCouponForOrder(@Param("orderId") long orderId);

    /** 결제 만료 주문의 예약 쿠폰을 기간에 맞는 상태로 해제한다. */
    int releaseReservedCouponForExpiredOrder(@Param("orderId") long orderId);

    /** 취소된 주문에 연결된 사용 완료 쿠폰을 기간에 맞는 상태로 되돌린다. */
    int restoreCouponForCanceledOrder(@Param("orderId") long orderId);
}
