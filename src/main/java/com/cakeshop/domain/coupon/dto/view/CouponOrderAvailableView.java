package com.cakeshop.domain.coupon.dto.view;

import com.cakeshop.domain.coupon.entity.DiscountType;
import java.math.BigDecimal;

/** 주문서 선택 목록에 표시할 사용 가능한 회원 쿠폰이다. */
public record CouponOrderAvailableView(
        Long memberCouponId,
        String name,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maximumDiscountAmount
) {
}
