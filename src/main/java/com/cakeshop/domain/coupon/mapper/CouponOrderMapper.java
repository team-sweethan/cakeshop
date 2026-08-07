package com.cakeshop.domain.coupon.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.cakeshop.domain.coupon.dto.view.CouponOrderDiscount;
import com.cakeshop.domain.coupon.dto.view.CouponOrderAvailableView;
import java.util.List;
import java.util.Optional;

/**
 * 주문·결제 취소 시 회원 쿠폰 상태를 복구하는 쿠폰 도메인의 SQL 계약이다.
 */
@Mapper
public interface CouponOrderMapper {

    List<CouponOrderAvailableView> findAvailableCouponsForMember(
            @Param("memberId") long memberId,
            @Param("orderAmount") java.math.BigDecimal orderAmount
    );

    Optional<CouponOrderDiscount> findAvailableCouponForOrder(
            @Param("memberCouponId") long memberCouponId,
            @Param("memberId") long memberId
    );

    Optional<CouponOrderDiscount> findAvailableCouponForOrderForUpdate(
            @Param("memberCouponId") long memberCouponId,
            @Param("memberId") long memberId
    );

    int reserveCouponForOrder(@Param("memberCouponId") long memberCouponId, @Param("orderId") long orderId);

    int useReservedCouponForOrder(@Param("orderId") long orderId);

    int releaseReservedCouponForExpiredOrder(@Param("orderId") long orderId);

    /** 취소된 주문에 연결된 사용 완료 쿠폰을 기간에 맞는 상태로 되돌린다. */
    int restoreCouponForCanceledOrder(@Param("orderId") long orderId);
}
