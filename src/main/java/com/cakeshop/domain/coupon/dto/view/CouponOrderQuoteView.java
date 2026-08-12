package com.cakeshop.domain.coupon.dto.view;

import java.math.BigDecimal;

/** 주문서가 표시하는 서버 계산 쿠폰 할인 견적이다. */
public record CouponOrderQuoteView(
        long memberCouponId,
        String name,
        BigDecimal discountAmount,
        BigDecimal finalAmount
) {
}
