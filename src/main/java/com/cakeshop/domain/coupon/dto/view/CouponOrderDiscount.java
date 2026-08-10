package com.cakeshop.domain.coupon.dto.view;

import com.cakeshop.domain.coupon.entity.DiscountType;
import java.math.BigDecimal;

/** 주문 금액 계산과 쿠폰 예약에 사용하는 쿠폰 정책 스냅샷이다. */
public record CouponOrderDiscount(
        Long memberCouponId,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount
) {
}
