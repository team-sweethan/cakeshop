package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponOrderDiscount;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 쿠폰 미리보기와 예약이 공유하는 할인 금액 계산 규칙이다. */
public final class CouponDiscountCalculator {

    private CouponDiscountCalculator() {
    }

    public static BigDecimal calculate(CouponOrderDiscount coupon, BigDecimal originalAmount) {
        if (originalAmount.compareTo(coupon.minimumOrderAmount()) < 0) {
            throw new BusinessException(CouponErrorCode.MINIMUM_ORDER_AMOUNT_NOT_MET);
        }

        BigDecimal discount = coupon.discountType() == DiscountType.PERCENTAGE
                ? originalAmount.multiply(coupon.discountValue())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                : coupon.discountValue().setScale(0, RoundingMode.DOWN);
        if (coupon.maximumDiscountAmount() != null) {
            discount = discount.min(coupon.maximumDiscountAmount());
        }
        return discount.min(originalAmount);
    }
}
