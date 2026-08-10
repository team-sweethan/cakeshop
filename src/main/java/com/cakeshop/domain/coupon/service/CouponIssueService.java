package com.cakeshop.domain.coupon.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.domain.member.service.MemberCouponQueryService;
import com.cakeshop.domain.order.service.OrderCouponQueryService;
import com.cakeshop.global.error.BusinessException;

/**
 * 발급 정책에 따라 자동 발급 대상을 찾고, 대상별 발급 작업을 분배한다.
 *
 * <p>ALL_MEMBERS·BIRTHDAY는 회원 행 잠금 없이 처리하고, FIRST_ORDER만 대상 한 명의
 * 짧은 트랜잭션에서 잠금과 주문 이력 재검증을 수행한다.</p>
 */
@Service
public class CouponIssueService {

    private final CouponMapper couponMapper;
    private final MemberCouponQueryService memberCouponQueryService;
    private final OrderCouponQueryService orderCouponQueryService;
    private final CouponMemberIssueService couponMemberIssueService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CouponIssueService(CouponMapper couponMapper,
                              MemberCouponQueryService memberCouponQueryService,
                              OrderCouponQueryService orderCouponQueryService,
                              CouponMemberIssueService couponMemberIssueService,
                              ApplicationEventPublisher eventPublisher,
                              Clock clock) {
        this.couponMapper = couponMapper;
        this.memberCouponQueryService = memberCouponQueryService;
        this.orderCouponQueryService = orderCouponQueryService;
        this.couponMemberIssueService = couponMemberIssueService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 쿠폰 등록이 확정된 뒤 기존 회원 대상 자동 발급을 요청한다. */
    public void issueOnCouponCreated(Coupon coupon) {
        eventPublisher.publishEvent(new CouponIssueRequestedEvent(coupon.getId(), coupon.getTargetType()));
    }

    /** 등록 트랜잭션이 커밋된 뒤 대상 목록을 읽고, 회원별 독립 발급 트랜잭션을 시작한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCouponIssueRequested(CouponIssueRequestedEvent event) {
        Long couponId = event.couponId();
        if (event.targetType() == CouponTargetType.ALL_MEMBERS) {
            issueMembers(couponId, memberCouponQueryService.getActiveMemberIds(), true, false);
        }
        if (event.targetType() == CouponTargetType.FIRST_ORDER) {
            List<Long> activeMemberIds = memberCouponQueryService.getActiveMemberIds();
            Set<Long> orderedMemberIds = Set.copyOf(
                    orderCouponQueryService.getMemberIdsWithOrderHistory(activeMemberIds)
            );
            issueMembers(couponId, activeMemberIds.stream()
                    .filter(memberId -> !orderedMemberIds.contains(memberId))
                    .toList(), true, true);
        }
    }

    /** 회원가입 완료 트랜잭션에서 신규 회원 한 명에게 적용할 쿠폰을 발급한다. */
    @Transactional
    public void issueNewMemberCoupons(Long memberId) {
        for (Coupon coupon : couponMapper.findCouponsByTargetType(CouponTargetType.NEW_MEMBERS)) {
            issueCurrentTransactionMember(coupon.getId(), memberId, false);
        }
    }

    /** 매시 정각에 해당 월 생일 회원을 찾고, 회원별 독립 트랜잭션으로 발급한다. */
    public void issueBirthdayCoupons() {
        int month = LocalDateTime.now(clock).getMonthValue();
        List<Long> birthdayMemberIds = memberCouponQueryService.getBirthdayMemberIds(month);
        for (Coupon coupon : couponMapper.findCouponsByTargetType(CouponTargetType.BIRTHDAY)) {
            issueMembers(coupon.getId(), birthdayMemberIds, false, false);
        }
    }

    /** 대량 대상은 각 회원의 발급 트랜잭션을 분리해 잠금 범위를 한 행으로 제한한다. */
    private void issueMembers(Long couponId,
                              List<Long> memberIds,
                              boolean allowBeforeStart,
                              boolean firstOrderOnly) {
        for (Long memberId : memberIds) {
            couponMemberIssueService.issueAutomatically(couponId, memberId, allowBeforeStart, firstOrderOnly);
        }
    }

    /** 신규 회원 발급은 회원가입 트랜잭션에서 한 명만 처리하므로 별도 행 잠금을 누적하지 않는다. */
    private void issueCurrentTransactionMember(Long couponId, Long memberId, boolean allowBeforeStart) {
        if (couponMapper.insertMemberCouponIfAbsent(couponId, memberId, allowBeforeStart) == 1
                && couponMapper.increaseIssuedQuantityIfAvailable(couponId) != 1) {
            throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
        }
    }
}
