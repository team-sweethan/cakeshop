package com.cakeshop.domain.coupon.service;

import static org.mockito.Mockito.verify;

import com.cakeshop.domain.coupon.mapper.CouponOrderMapper;
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
}
