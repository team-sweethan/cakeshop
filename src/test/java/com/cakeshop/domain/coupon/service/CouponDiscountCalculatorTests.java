package com.cakeshop.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.coupon.dto.view.CouponOrderDiscount;
import com.cakeshop.domain.coupon.entity.DiscountType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CouponDiscountCalculatorTests {

    @Test
    void calculate_percentageDiscount_appliesMaximumAndOriginalAmountCap() {
        CouponOrderDiscount coupon = new CouponOrderDiscount(
                10L,
                DiscountType.PERCENTAGE,
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(5_000)
        );

        BigDecimal discount = CouponDiscountCalculator.calculate(coupon, BigDecimal.valueOf(20_000));

        assertThat(discount).isEqualByComparingTo("5000");
    }
}
