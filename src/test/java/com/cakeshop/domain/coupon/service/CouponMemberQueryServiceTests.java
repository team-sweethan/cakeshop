package com.cakeshop.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cakeshop.domain.coupon.dto.view.CustomerCouponQueryView;
import com.cakeshop.domain.coupon.dto.view.CustomerCouponView;
import com.cakeshop.domain.coupon.entity.CustomerCouponStatus;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.mapper.CouponMemberMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

@ExtendWith(MockitoExtension.class)
class CouponMemberQueryServiceTests {

    @Mock
    private CouponMemberMapper couponMemberMapper;

    @InjectMocks
    private CouponMemberQueryService couponMemberQueryService;

    @Test
    void getMemberCoupons_percentageCoupon_returnsCustomerDisplayValues() {
        PageRequest request = new PageRequest(1, 10);
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 12, 0);
        when(couponMemberMapper.countMemberCoupons(1L)).thenReturn(1L);
        when(couponMemberMapper.findMemberCoupons(1L, 10, 0)).thenReturn(List.of(
                new CustomerCouponQueryView(
                        10L, "생일 쿠폰", DiscountType.PERCENTAGE, BigDecimal.TEN,
                        BigDecimal.valueOf(20_000), BigDecimal.valueOf(5_000),
                        CustomerCouponStatus.AVAILABLE, now, now.plusDays(20))
        ));

        PageResult<CustomerCouponView> result = couponMemberQueryService.getMemberCoupons(1L, request);

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent()).singleElement().satisfies(coupon -> {
            assertThat(coupon.discountDescription()).isEqualTo("10% 할인 (최대 5,000원)");
            assertThat(coupon.conditionDescription()).isEqualTo("20,000원 이상 구매 시");
        });
    }
}
