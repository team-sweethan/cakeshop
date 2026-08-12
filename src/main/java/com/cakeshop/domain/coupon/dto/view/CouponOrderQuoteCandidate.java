package com.cakeshop.domain.coupon.dto.view;

import com.cakeshop.domain.coupon.entity.DiscountType;
import java.math.BigDecimal;

/** 쿠폰 도메인 내부에서 주문 견적을 계산하기 위한 현재 사용 가능 쿠폰 정책이다. */
public record CouponOrderQuoteCandidate(
        long memberCouponId,
        String name,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount
) {
    public CouponOrderDiscount toDiscountPolicy() {
        return new CouponOrderDiscount(
                memberCouponId,
                discountType,
                discountValue,
                minimumOrderAmount,
                maximumDiscountAmount
        );
    }
}
