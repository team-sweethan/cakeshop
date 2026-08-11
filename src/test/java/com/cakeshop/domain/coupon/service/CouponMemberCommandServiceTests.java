package com.cakeshop.domain.coupon.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.mapper.CouponMapper;

/** 신규 회원 발급 공개 명령이 회원별 발급 트랜잭션으로 위임되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class CouponMemberCommandServiceTests {

    @Mock
    private CouponMapper couponMapper;

    @InjectMocks
    private CouponMemberCommandService couponMemberCommandService;

    @Test
    void issueNewMemberCoupons_issuesEachNewMemberCouponInJoinTransaction() {
        Coupon firstCoupon = coupon(1L);
        Coupon secondCoupon = coupon(2L);
        when(couponMapper.findCouponsByTargetType(CouponTargetType.NEW_MEMBERS))
                .thenReturn(List.of(firstCoupon, secondCoupon));
        when(couponMapper.insertMemberCouponIfAbsent(1L, 10L, false)).thenReturn(1);
        when(couponMapper.insertMemberCouponIfAbsent(2L, 10L, false)).thenReturn(1);
        when(couponMapper.increaseIssuedQuantityIfAvailable(1L)).thenReturn(1);
        when(couponMapper.increaseIssuedQuantityIfAvailable(2L)).thenReturn(1);

        couponMemberCommandService.issueNewMemberCoupons(10L);

        verify(couponMapper).insertMemberCouponIfAbsent(1L, 10L, false);
        verify(couponMapper).insertMemberCouponIfAbsent(2L, 10L, false);
        verify(couponMapper).increaseIssuedQuantityIfAvailable(1L);
        verify(couponMapper).increaseIssuedQuantityIfAvailable(2L);
    }

    private Coupon coupon(Long id) {
        Coupon coupon = new Coupon();
        coupon.setId(id);
        return coupon;
    }
}
