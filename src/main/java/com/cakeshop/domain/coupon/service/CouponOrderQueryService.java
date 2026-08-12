package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponOrderAvailableView;
import com.cakeshop.domain.coupon.dto.view.CouponOrderDiscount;
import java.math.BigDecimal;
import com.cakeshop.domain.coupon.mapper.CouponOrderMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 주환(이정후)
 * 담당자 : 이정후
 * 작성일 : 2026-08-10
 * 기능 : 주문서 사용 가능 쿠폰 조회 계약
 * 설명 : 일반 주문에는 사용 가능 쿠폰을, 0원 결제를 지원하지 않는 수제 주문에는
 *       적용 후 최종금액이 양수인 쿠폰만 제공한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CouponOrderQueryService {

    private final CouponOrderMapper couponOrderMapper;

    /** 현재 주문 금액을 만족하는 사용 가능 쿠폰만 주문서 선택 목록으로 제공한다. */
    @Transactional(readOnly = true)
    public List<CouponOrderAvailableView> getAvailableCouponsForMember(
            long memberId, BigDecimal orderAmount
    ) {
        if (orderAmount == null || orderAmount.signum() <= 0) {
            return List.of();
        }
        return couponOrderMapper.findAvailableCouponsForMember(memberId, orderAmount);
    }

    /**
     * 0원 결제를 지원하지 않는 수제 주문서에 표시할 쿠폰만 조회한다.
     * 일반 주문의 0원 결제 정책에는 사용하지 않는다.
     */
    @Transactional(readOnly = true)
    public List<CouponOrderAvailableView> getAvailableCouponsWithPositiveFinalAmountForMember(
            long memberId, BigDecimal orderAmount
    ) {
        return getAvailableCouponsForMember(memberId, orderAmount).stream()
                .filter(coupon -> hasPositiveFinalAmount(coupon, orderAmount))
                .toList();
    }

    private boolean hasPositiveFinalAmount(CouponOrderAvailableView coupon, BigDecimal orderAmount) {
        CouponOrderDiscount discount = new CouponOrderDiscount(
                coupon.memberCouponId(),
                coupon.discountType(),
                coupon.discountValue(),
                coupon.minimumOrderAmount(),
                coupon.maximumDiscountAmount()
        );
        return orderAmount.subtract(CouponDiscountCalculator.calculate(discount, orderAmount))
                .signum() > 0;
    }

}
