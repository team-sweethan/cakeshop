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
     * ******************************
     * 작성자 : 주환
     * 담당자 : 이정후
     * 작성일 : 2026-08-12
     * 기능 : 수제 주문 양수 최종금액 쿠폰 조회
     * 설명 : 수제 주문은 0원 결제를 제공하지 않아, 공통 후보 중 서버 할인 계산 뒤
     *       최종 결제 금액이 양수인 쿠폰만 주문서에 제공한다.
     * ******************************
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
