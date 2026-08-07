package com.cakeshop.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.coupon.dto.view.CouponOrderDiscount;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.mapper.CouponOrderMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CouponOrderCommandServiceTests {

    @Mock
    private CouponOrderMapper couponOrderMapper;

    @InjectMocks
    private CouponOrderCommandService couponOrderCommandService;

    @Test
    void restoreCouponForCanceledOrder_noUsedCoupon_isIdempotent() {
        couponOrderCommandService.restoreCouponForCanceledOrder(10L);

        verify(couponOrderMapper).restoreCouponForCanceledOrder(10L);
    }

    @Test
    void reserveCouponForOrder_fixedDecimalDiscount_roundsDownToWon() {
        CouponOrderDiscount coupon = new CouponOrderDiscount(
                20L, DiscountType.FIXED_AMOUNT, new BigDecimal("1000.50"),
                BigDecimal.ZERO, null
        );
        when(couponOrderMapper.findAvailableCouponForOrderForUpdate(20L, 10L))
                .thenReturn(Optional.of(coupon));
        when(couponOrderMapper.reserveCouponForOrder(20L, 30L)).thenReturn(1);

        BigDecimal discount = couponOrderCommandService.reserveCouponForOrder(
                10L, 20L, 30L, BigDecimal.valueOf(5_000)
        );

        assertThat(discount).isEqualByComparingTo("1000");
    }
}
