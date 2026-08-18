package com.cakeshop.domain.coupon.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.domain.member.service.MemberCouponQueryService;
import com.cakeshop.domain.order.service.OrderCouponQueryService;

@ExtendWith(MockitoExtension.class)
class CouponMemberIssueServiceTests {

    @Mock
    private CouponMapper couponMapper;

    @Mock
    private MemberCouponQueryService memberCouponQueryService;

    @Mock
    private OrderCouponQueryService orderCouponQueryService;

    @InjectMocks
    private CouponMemberIssueService couponMemberIssueService;

    @Test
    void issueAutomatically_allMembers_doesNotLockMemberRow() {
        when(couponMapper.insertMemberCouponIfAbsent(1L, 2L, true)).thenReturn(1);
        when(couponMapper.increaseIssuedQuantityIfAvailable(1L)).thenReturn(1);

        boolean issued = couponMemberIssueService.issueAutomatically(1L, 2L, true, false);

        assertTrue(issued);
        verify(memberCouponQueryService, never()).lockActiveCouponIssuableMember(2L);
        verify(couponMapper).increaseIssuedQuantityIfAvailable(1L);
    }

    @Test
    void issueAutomatically_firstOrder_rechecksAfterMemberLock() {
        when(memberCouponQueryService.lockActiveCouponIssuableMember(2L)).thenReturn(true);
        when(orderCouponQueryService.hasOrderHistory(2L)).thenReturn(true);

        boolean issued = couponMemberIssueService.issueAutomatically(1L, 2L, true, true);

        assertFalse(issued);
        verify(memberCouponQueryService).lockActiveCouponIssuableMember(2L);
        verify(orderCouponQueryService).hasOrderHistory(2L);
        verify(couponMapper, never()).insertMemberCouponIfAbsent(1L, 2L, true);
    }
}
