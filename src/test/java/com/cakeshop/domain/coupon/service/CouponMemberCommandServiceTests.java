package com.cakeshop.domain.coupon.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cakeshop.domain.coupon.mapper.CouponMemberMapper;

/** 신규 회원 발급 공개 명령이 회원별 발급 트랜잭션으로 위임되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class CouponMemberCommandServiceTests {

    @Mock
    private CouponMemberMapper couponMemberMapper;

    @InjectMocks
    private CouponMemberCommandService couponMemberCommandService;

    @Test
    void issueNewMemberCoupons_issuesEachNewMemberCouponInJoinTransaction() {
        when(couponMemberMapper.findAvailableNewMemberCouponIds()).thenReturn(List.of(1L, 2L));
        when(couponMemberMapper.insertNewMemberCouponIfAbsent(1L, 10L)).thenReturn(1);
        when(couponMemberMapper.insertNewMemberCouponIfAbsent(2L, 10L)).thenReturn(1);
        when(couponMemberMapper.increaseNewMemberCouponIssuedQuantity(1L)).thenReturn(1);
        when(couponMemberMapper.increaseNewMemberCouponIssuedQuantity(2L)).thenReturn(1);

        couponMemberCommandService.issueNewMemberCoupons(10L);

        verify(couponMemberMapper).insertNewMemberCouponIfAbsent(1L, 10L);
        verify(couponMemberMapper).insertNewMemberCouponIfAbsent(2L, 10L);
        verify(couponMemberMapper).increaseNewMemberCouponIssuedQuantity(1L);
        verify(couponMemberMapper).increaseNewMemberCouponIssuedQuantity(2L);
    }
}
