package com.cakeshop.domain.coupon.service;

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

    public CouponIssueService(CouponMapper couponMapper,
                              MemberCouponQueryService memberCouponQueryService,
                              OrderCouponQueryService orderCouponQueryService) {
        this.couponMapper = couponMapper;
        this.memberCouponQueryService = memberCouponQueryService;
        this.orderCouponQueryService = orderCouponQueryService;
    }

    /** 쿠폰 등록 직후 정책상 즉시 발급해야 하는 기존 회원에게 발급한다. */
    @Transactional
    public void issueOnCouponCreated(Coupon coupon) {
        if (coupon.getTargetType() == CouponTargetType.ALL_MEMBERS) {
            issueMembers(coupon.getId(), memberCouponQueryService.getActiveMemberIds());
        }
        if (coupon.getTargetType() == CouponTargetType.FIRST_ORDER) {
            List<Long> activeMemberIds = memberCouponQueryService.getActiveMemberIds();
            Set<Long> orderedMemberIds = Set.copyOf(
                    orderCouponQueryService.getMemberIdsWithOrderHistory(activeMemberIds)
            );
            issueMembers(coupon.getId(), activeMemberIds.stream()
                    .filter(memberId -> !orderedMemberIds.contains(memberId))
                    .toList());
        }
    }

    /** 회원가입 완료 후 member 도메인에서 호출하는 신규 회원 쿠폰 발급 진입점이다. */
    @Transactional
    public void issueNewMemberCoupons(Long memberId) {
        for (Coupon coupon : couponMapper.findCouponsByTargetType(CouponTargetType.NEW_MEMBERS)) {
            issueMembers(coupon.getId(), List.of(memberId));
        }
    }

    /** 매월 등록된 생일 쿠폰을 매시 정각 스케줄러가 이번 달 생일 회원에게 생일 쿠폰을 발급한다. */
    @Transactional
    public void issueBirthdayCoupons() {
        int month = LocalDateTime.now().getMonthValue();
        List<Long> birthdayMemberIds = memberCouponQueryService.getBirthdayMemberIds(month);
        for (Coupon coupon : couponMapper.findCouponsByTargetType(CouponTargetType.BIRTHDAY)) {
            issueMembers(coupon.getId(), birthdayMemberIds);
        }
    }

    private void issueMembers(Long couponId, List<Long> memberIds) {
        Coupon coupon = couponMapper.findCouponByIdForUpdate(couponId)
                .orElseThrow(() -> new BusinessException(CouponErrorCode.NOT_FOUND));
        if (coupon.getStatus() != CouponStatus.ACTIVE || !coupon.getExpiresAt().isAfter(LocalDateTime.now())) {
            return;
        }

        int remaining = coupon.getTotalQuantity() == null
                ? Integer.MAX_VALUE
                : coupon.getTotalQuantity() - coupon.getIssuedQuantity();
        for (Long memberId : memberIds) {
            if (remaining <= 0) {
                return;
            }
            if (couponMapper.insertMemberCouponIfAbsent(couponId, memberId) == 1) {
                if (couponMapper.increaseIssuedQuantityIfAvailable(couponId) != 1) {
                    throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
                }
                remaining--;
            }
        }
    }
}
