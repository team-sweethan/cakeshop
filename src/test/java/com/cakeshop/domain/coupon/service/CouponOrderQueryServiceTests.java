package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponOrderAvailableView;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.mapper.CouponOrderMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponOrderQueryServiceTests {

    @Mock
    private CouponOrderMapper couponOrderMapper;

    @Test
    void getAvailableCouponsForMember_nonPositiveOrderAmount_returnsNoCoupons() {
        CouponOrderQueryService service = new CouponOrderQueryService(couponOrderMapper);

        assertThat(service.getAvailableCouponsForMember(10L, BigDecimal.ZERO)).isEmpty();

        verifyNoInteractions(couponOrderMapper);
    }

    @Test
    void getAvailableCouponsForMember_positiveOrderAmount_returnsMapperResult() {
        List<CouponOrderAvailableView> coupons = List.of();
        when(couponOrderMapper.findAvailableCouponsForMember(10L, BigDecimal.valueOf(30_000)))
                .thenReturn(coupons);
        CouponOrderQueryService service = new CouponOrderQueryService(couponOrderMapper);

        assertThat(service.getAvailableCouponsForMember(10L, BigDecimal.valueOf(30_000)))
                .isSameAs(coupons);

        verify(couponOrderMapper)
                .findAvailableCouponsForMember(10L, BigDecimal.valueOf(30_000));
    }

    @Test
    void getAvailableCouponsWithPositiveFinalAmountForMember_excludesZeroAmountCoupons() {
        BigDecimal orderAmount = BigDecimal.valueOf(10_000);
        CouponOrderAvailableView zeroAmountCoupon = new CouponOrderAvailableView(
                1L, "전액 할인", DiscountType.FIXED_AMOUNT, orderAmount,
                BigDecimal.ZERO, null
        );
        CouponOrderAvailableView positiveAmountCoupon = new CouponOrderAvailableView(
                2L, "일부 할인", DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(9_000),
                BigDecimal.ZERO, null
        );
        when(couponOrderMapper.findAvailableCouponsForMember(10L, orderAmount))
                .thenReturn(List.of(zeroAmountCoupon, positiveAmountCoupon));
        CouponOrderQueryService service = new CouponOrderQueryService(couponOrderMapper);

        assertThat(service.getAvailableCouponsWithPositiveFinalAmountForMember(10L, orderAmount))
                .containsExactly(positiveAmountCoupon);
    }
}
