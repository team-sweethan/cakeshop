package com.cakeshop.domain.coupon.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.domain.member.service.MemberCouponQueryService;
import com.cakeshop.domain.order.service.OrderCouponQueryService;

/**
 * 발급 정책에 따라 자동 발급 대상을 찾고, 대상별 발급 작업을 분배한다.
 *
 * <p>ALL_MEMBERS·BIRTHDAY는 대상 회원을 조회한 시점의 회원 상태를 기준으로 회원 행 잠금 없이 처리한다.
 * FIRST_ORDER만 대상 한 명의 짧은 트랜잭션에서 잠금과 주문 이력 재검증을 수행한다.</p>
 */
@Service
@Slf4j
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

    /** 매시 정각에 해당 월 생일 회원을 조회하고, 조회 시점의 대상에게 독립 트랜잭션으로 발급한다. */
    public void issueBirthdayCoupons() {
        int month = LocalDateTime.now(clock).getMonthValue();
        List<Long> birthdayMemberIds = memberCouponQueryService.getBirthdayMemberIds(month);
        List<Coupon> birthdayCoupons = couponMapper.findCouponsByTargetType(CouponTargetType.BIRTHDAY);
        if (birthdayCoupons.isEmpty()) {
            log.info("생일 쿠폰 자동 발급 대상 쿠폰이 없습니다. month={}", month);
        }
        for (Coupon coupon : birthdayCoupons) {
            BirthdayIssueResult result = issueBirthdayMembers(coupon.getId(), birthdayMemberIds);
            logBirthdayIssueResult(coupon.getId(), month, birthdayMemberIds.size(), result);
        }
    }

    /** 생일 쿠폰은 회원별 오류를 모아 다른 대상 회원의 발급을 계속 처리한다. */
    private BirthdayIssueResult issueBirthdayMembers(Long couponId, List<Long> memberIds) {
        int issuedMemberCount = 0;
        int skippedMemberCount = 0;
        int failedMemberCount = 0;

        for (Long memberId : memberIds) {
            try {
                if (couponMemberIssueService.issueAutomatically(couponId, memberId, false, false)) {
                    issuedMemberCount++;
                } else {
                    skippedMemberCount++;
                }
            } catch (RuntimeException ignored) {
                // 회원 식별 정보는 운영 로그에 남기지 않고, 배치 종료 후 실패 건수만 기록한다.
                failedMemberCount++;
            }
        }
        return new BirthdayIssueResult(issuedMemberCount, skippedMemberCount, failedMemberCount);
    }

    /** 쿠폰별 자동 발급 결과를 실제 신규 발급·제외·오류로 나눠 기록한다. */
    private void logBirthdayIssueResult(Long couponId,
                                        int month,
                                        int candidateMemberCount,
                                        BirthdayIssueResult result) {
        if (result.failedMemberCount() == 0) {
            log.info(
                    "생일 쿠폰 자동 발급을 완료했습니다. couponId={}, month={}, candidateMemberCount={}, issuedMemberCount={}, skippedMemberCount={}",
                    couponId,
                    month,
                    candidateMemberCount,
                    result.issuedMemberCount(),
                    result.skippedMemberCount()
            );
            return;
        }
        log.warn(
                "생일 쿠폰 자동 발급 중 일부 회원 발급에 실패했습니다. couponId={}, month={}, candidateMemberCount={}, issuedMemberCount={}, skippedMemberCount={}, failedCount={}",
                couponId,
                month,
                candidateMemberCount,
                result.issuedMemberCount(),
                result.skippedMemberCount(),
                result.failedMemberCount()
        );
    }

    /** 대량 대상은 각 회원의 발급 트랜잭션을 분리해 잠금 범위를 한 행으로 제한한다. */
    private int issueMembers(Long couponId,
                             List<Long> memberIds,
                             boolean allowBeforeStart,
                             boolean firstOrderOnly) {
        int issuedMemberCount = 0;
        for (Long memberId : memberIds) {
            if (couponMemberIssueService.issueAutomatically(
                    couponId, memberId, allowBeforeStart, firstOrderOnly
            )) {
                issuedMemberCount++;
            }
        }
        return issuedMemberCount;
    }

    /** 생일 쿠폰 한 장에 대한 회원별 발급 처리 결과다. */
    private record BirthdayIssueResult(
            int issuedMemberCount,
            int skippedMemberCount,
            int failedMemberCount
    ) {
    }
}
