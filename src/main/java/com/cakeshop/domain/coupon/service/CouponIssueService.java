package com.cakeshop.domain.coupon.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.domain.member.service.MemberCouponQueryService;
import com.cakeshop.domain.order.service.OrderCouponQueryService;
import com.cakeshop.global.error.BusinessException;

/**
 * 대상 정책에 따라 쿠폰 발급과 수량 차감을 처리한다.
 *
 * <p>작성자: 이정후 - 회원·주문 조회 Service를 연동해 자동 발급 대상을 결정한다.</p>
 */
@Service
public class CouponIssueService {

    private final CouponMapper couponMapper;
    private final MemberCouponQueryService memberCouponQueryService;
    private final OrderCouponQueryService orderCouponQueryService;
    private final Clock clock;

    public CouponIssueService(CouponMapper couponMapper,
                              MemberCouponQueryService memberCouponQueryService,
                              OrderCouponQueryService orderCouponQueryService,
                              Clock clock) {
        this.couponMapper = couponMapper;
        this.memberCouponQueryService = memberCouponQueryService;
        this.orderCouponQueryService = orderCouponQueryService;
        this.clock = clock;
    }

    /** 쿠폰 등록 직후 정책상 즉시 발급해야 하는 기존 회원에게 발급한다. */
    @Transactional
    public void issueOnCouponCreated(Coupon coupon) {
        if (coupon.getTargetType() == CouponTargetType.ALL_MEMBERS) {
            issueMembers(coupon.getId(), memberCouponQueryService.getActiveMemberIds(), true, false);
        }
        if (coupon.getTargetType() == CouponTargetType.FIRST_ORDER) {
            List<Long> activeMemberIds = memberCouponQueryService.getActiveMemberIds();
            Set<Long> orderedMemberIds = Set.copyOf(
                    orderCouponQueryService.getMemberIdsWithOrderHistory(activeMemberIds)
            );
            issueMembers(coupon.getId(), activeMemberIds.stream()
                    .filter(memberId -> !orderedMemberIds.contains(memberId))
                    .toList(), true, true);
        }
    }

    /** 회원가입 완료 후 member 도메인에서 호출하는 신규 회원 쿠폰 발급 진입점이다. */
    @Transactional
    public void issueNewMemberCoupons(Long memberId) {
        for (Coupon coupon : couponMapper.findCouponsByTargetType(CouponTargetType.NEW_MEMBERS)) {
            issueMembers(coupon.getId(), List.of(memberId), false, false);
        }
    }

    /** 매월 등록된 생일 쿠폰을 매시 정각 스케줄러가 이번 달 생일 회원에게 생일 쿠폰을 발급한다. */
    @Transactional
    public void issueBirthdayCoupons() {
        // 스케줄러와 동일한 Asia/Seoul 기준으로 생일 대상 월을 계산한다.
        int month = LocalDateTime.now(clock).getMonthValue();
        List<Long> birthdayMemberIds = memberCouponQueryService.getBirthdayMemberIds(month);
        for (Coupon coupon : couponMapper.findCouponsByTargetType(CouponTargetType.BIRTHDAY)) {
            issueMembers(coupon.getId(), birthdayMemberIds, false, false);
        }
    }

    /**
     * 회원 쿠폰을 발급하고 발급 수량을 증가시킨다.
     * 등록 직후 일괄 발급만 시작 전 발급 이력 생성을 허용하며, 실제 사용 가능 시각은 쿠폰 기간이 판단한다.
     */
    private void issueMembers(Long couponId,
                              List<Long> memberIds,
                              boolean allowBeforeStart,
                              boolean firstOrderOnly) {
        for (Long memberId : memberIds) {
            if (!memberCouponQueryService.lockActiveCouponIssuableMember(memberId)) {
                continue;
            }
            // 후보 조회 뒤 주문이 생성될 수 있으므로 INSERT 직전에 최신 주문 이력을 다시 확인한다.
            if (firstOrderOnly && orderCouponQueryService.hasOrderHistory(memberId)) {
                continue;
            }
            if (couponMapper.insertMemberCouponIfAbsent(
                    couponId, memberId, allowBeforeStart) == 1) {
                if (couponMapper.increaseIssuedQuantityIfAvailable(couponId) != 1) {
                    throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
                }
            }
        }
    }
}
