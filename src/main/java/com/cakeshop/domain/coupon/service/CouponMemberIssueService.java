package com.cakeshop.domain.coupon.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.domain.member.service.MemberCouponQueryService;
import com.cakeshop.domain.order.service.OrderCouponQueryService;
import com.cakeshop.global.error.BusinessException;

/**
 * 자동 발급 대상 한 명을 독립 트랜잭션으로 처리한다.
 *
 * <p>대량 발급 중 한 회원의 행 잠금이 다음 회원 발급까지 유지되지 않도록 분리한다.
 * 첫 주문 대상만 회원 행을 잠근 뒤 주문 이력을 다시 확인한다.</p>
 */
@Service
public class CouponMemberIssueService {

    private final CouponMapper couponMapper;
    private final MemberCouponQueryService memberCouponQueryService;
    private final OrderCouponQueryService orderCouponQueryService;

    public CouponMemberIssueService(CouponMapper couponMapper,
                                    MemberCouponQueryService memberCouponQueryService,
                                    OrderCouponQueryService orderCouponQueryService) {
        this.couponMapper = couponMapper;
        this.memberCouponQueryService = memberCouponQueryService;
        this.orderCouponQueryService = orderCouponQueryService;
    }

    /** 자동 발급 한 건을 처리해 실제 신규 발급 여부를 반환하고, FIRST_ORDER만 최신 주문 이력을 재검증한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean issueAutomatically(Long couponId,
                                      Long memberId,
                                      boolean allowBeforeStart,
                                      boolean firstOrderOnly) {
        if (firstOrderOnly && !memberCouponQueryService.lockActiveCouponIssuableMember(memberId)) {
            return false;
        }
        if (firstOrderOnly && orderCouponQueryService.hasOrderHistory(memberId)) {
            return false;
        }
        boolean issued = couponMapper.insertMemberCouponIfAbsent(couponId, memberId, allowBeforeStart) == 1;
        if (issued && couponMapper.increaseIssuedQuantityIfAvailable(couponId) != 1) {
            throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
        }
        return issued;
    }
}
