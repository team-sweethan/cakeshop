package com.cakeshop.domain.coupon.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.domain.member.service.MemberCouponQueryService;
import com.cakeshop.domain.order.service.OrderCouponQueryService;
import com.cakeshop.global.error.BusinessException;

@ExtendWith(MockitoExtension.class)
class CouponIssueServiceTests {

    @Mock
    private CouponMapper couponMapper;

    @Mock
    private MemberCouponQueryService memberCouponQueryService;

    @Mock
    private OrderCouponQueryService orderCouponQueryService;

    @Mock
    private CouponMemberIssueService couponMemberIssueService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private CouponIssueService couponIssueService;

    @Test
    void issueOnCouponCreated_allMembers_publishesAfterCommitEvent() {
        Coupon coupon = coupon(CouponTargetType.ALL_MEMBERS, LocalDateTime.now().plusDays(1));

        couponIssueService.issueOnCouponCreated(coupon);

        verify(eventPublisher).publishEvent(new CouponIssueRequestedEvent(1L, CouponTargetType.ALL_MEMBERS));
    }

    @Test
    void handleCouponIssueRequested_allMembers_delegatesEachMemberWithoutMemberLock() {
        when(memberCouponQueryService.getActiveMemberIds()).thenReturn(List.of(2L, 3L));

        couponIssueService.handleCouponIssueRequested(
                new CouponIssueRequestedEvent(1L, CouponTargetType.ALL_MEMBERS)
        );

        verify(couponMemberIssueService).issueAutomatically(1L, 2L, true, false);
        verify(couponMemberIssueService).issueAutomatically(1L, 3L, true, false);
        verify(memberCouponQueryService, never()).lockActiveCouponIssuableMember(2L);
    }

    @Test
    void handleCouponIssueRequested_firstOrder_excludesKnownOrderHistoryThenDelegatesRecheck() {
        when(memberCouponQueryService.getActiveMemberIds()).thenReturn(List.of(2L, 3L));
        when(orderCouponQueryService.getMemberIdsWithOrderHistory(List.of(2L, 3L))).thenReturn(List.of(3L));

        couponIssueService.handleCouponIssueRequested(
                new CouponIssueRequestedEvent(1L, CouponTargetType.FIRST_ORDER)
        );

        verify(couponMemberIssueService).issueAutomatically(1L, 2L, true, true);
        verify(couponMemberIssueService, never()).issueAutomatically(1L, 3L, true, true);
    }

    @Test
    void issueBirthdayCoupons_delegatesCurrentMonthCandidatesToEachActiveBirthdayCoupon() {
        Coupon firstCoupon = coupon(CouponTargetType.BIRTHDAY, LocalDateTime.now().minusDays(1));
        firstCoupon.setId(1L);
        Coupon secondCoupon = coupon(CouponTargetType.BIRTHDAY, LocalDateTime.now().minusDays(1));
        secondCoupon.setId(2L);
        when(memberCouponQueryService.getBirthdayMemberIds(org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(10L, 20L));
        when(couponMapper.findCouponsByTargetType(CouponTargetType.BIRTHDAY))
                .thenReturn(List.of(firstCoupon, secondCoupon));
        when(couponMemberIssueService.issueAutomatically(1L, 10L, false, false)).thenReturn(true);
        when(couponMemberIssueService.issueAutomatically(1L, 20L, false, false)).thenReturn(false);
        when(couponMemberIssueService.issueAutomatically(2L, 10L, false, false)).thenReturn(true);
        when(couponMemberIssueService.issueAutomatically(2L, 20L, false, false)).thenReturn(false);

        couponIssueService.issueBirthdayCoupons();

        verify(couponMemberIssueService).issueAutomatically(1L, 10L, false, false);
        verify(couponMemberIssueService).issueAutomatically(1L, 20L, false, false);
        verify(couponMemberIssueService).issueAutomatically(2L, 10L, false, false);
        verify(couponMemberIssueService).issueAutomatically(2L, 20L, false, false);
    }

    @Test
    void issueBirthdayCoupons_continuesOtherMembersWhenOneMemberIssueFails() {
        Coupon coupon = coupon(CouponTargetType.BIRTHDAY, LocalDateTime.now().minusDays(1));
        coupon.setId(10L);
        when(memberCouponQueryService.getBirthdayMemberIds(org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(10L, 20L));
        when(couponMapper.findCouponsByTargetType(CouponTargetType.BIRTHDAY)).thenReturn(List.of(coupon));
        when(couponMemberIssueService.issueAutomatically(10L, 10L, false, false))
                .thenThrow(new BusinessException(CouponErrorCode.UPDATE_FAILED));
        when(couponMemberIssueService.issueAutomatically(10L, 20L, false, false)).thenReturn(true);

        assertDoesNotThrow(() -> couponIssueService.issueBirthdayCoupons());

        verify(couponMemberIssueService).issueAutomatically(10L, 10L, false, false);
        verify(couponMemberIssueService).issueAutomatically(10L, 20L, false, false);
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
