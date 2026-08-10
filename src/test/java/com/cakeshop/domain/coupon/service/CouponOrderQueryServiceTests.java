package com.cakeshop.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.coupon.dto.view.CouponOrderDiscount;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.mapper.CouponOrderMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CouponOrderQueryServiceTests {

    @Mock
    private CouponOrderMapper couponOrderMapper;

    @Test
    void previewDiscount_fixedDecimalDiscount_roundsDownToWon() {
        CouponOrderQueryService service = new CouponOrderQueryService(couponOrderMapper);
        when(couponOrderMapper.findAvailableCouponForOrder(10L, 3L)).thenReturn(Optional.of(
                new CouponOrderDiscount(10L, DiscountType.FIXED_AMOUNT, new BigDecimal("1000.50"),
                        BigDecimal.ZERO, null)
        ));

        CouponOrderQueryService.CouponPricePreview result = service.previewDiscount(
                3L, 10L, BigDecimal.valueOf(5_000)
        );

        assertThat(result.discountAmount()).isEqualByComparingTo("1000");
        assertThat(result.finalAmount()).isEqualByComparingTo("4000");
    }
}
