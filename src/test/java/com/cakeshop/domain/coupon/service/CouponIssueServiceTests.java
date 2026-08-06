package com.cakeshop.domain.coupon.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.domain.member.service.MemberCouponQueryService;
import com.cakeshop.domain.order.service.OrderCouponQueryService;

@ExtendWith(MockitoExtension.class)
class CouponIssueServiceTests {

    @Mock
    private CouponMapper couponMapper;

    @Mock
    private MemberCouponQueryService memberCouponQueryService;

    @Mock
    private OrderCouponQueryService orderCouponQueryService;

    @InjectMocks
    private CouponIssueService couponIssueService;

    @Test
    void issueOnCouponCreated_allMembersFutureCouponAllowsIssuanceBeforeStart() {
        Coupon coupon = coupon(CouponTargetType.ALL_MEMBERS, LocalDateTime.now().plusDays(1));
        when(memberCouponQueryService.getActiveMemberIds()).thenReturn(List.of(2L));
        when(couponMapper.findCouponByIdForUpdate(1L)).thenReturn(Optional.of(coupon));
        when(couponMapper.insertMemberCouponIfAbsent(1L, 2L, true, false)).thenReturn(1);
        when(couponMapper.increaseIssuedQuantityIfAvailable(1L)).thenReturn(1);

        couponIssueService.issueOnCouponCreated(coupon);

        verify(couponMapper).insertMemberCouponIfAbsent(1L, 2L, true, false);
        verify(couponMapper).increaseIssuedQuantityIfAvailable(1L);
    }

    @Test
    void issueOnCouponCreated_firstOrderRechecksOrderHistoryBeforeIssuance() {
        Coupon coupon = coupon(CouponTargetType.FIRST_ORDER, LocalDateTime.now().plusDays(1));
        when(memberCouponQueryService.getActiveMemberIds()).thenReturn(List.of(2L));
        when(orderCouponQueryService.getMemberIdsWithOrderHistory(List.of(2L))).thenReturn(List.of());
        when(couponMapper.findCouponByIdForUpdate(1L)).thenReturn(Optional.of(coupon));
        when(orderCouponQueryService.hasOrderHistory(2L)).thenReturn(true);

        couponIssueService.issueOnCouponCreated(coupon);

        verify(orderCouponQueryService).hasOrderHistory(2L);
        verify(couponMapper, never()).insertMemberCouponIfAbsent(1L, 2L, true, true);
    }

    private Coupon coupon(CouponTargetType targetType, LocalDateTime startsAt) {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setTargetType(targetType);
        coupon.setStatus(CouponStatus.ACTIVE);
        coupon.setStartsAt(startsAt);
        coupon.setExpiresAt(LocalDateTime.now().plusDays(30));
        coupon.setIssuedQuantity(0);
        return coupon;
    }
}
