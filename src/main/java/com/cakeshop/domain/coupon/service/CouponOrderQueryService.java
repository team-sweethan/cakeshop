package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponOrderAvailableView;
import com.cakeshop.domain.coupon.dto.view.CouponOrderDiscount;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.cakeshop.domain.coupon.mapper.CouponOrderMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 담당자가 일반 주문서에 표시할 사용 가능 쿠폰과 예상 할인 금액을 조회할 때 사용한다.
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
        return couponOrderMapper.findAvailableCouponsForMember(memberId, orderAmount);
    }

    /** 현재 상품·옵션 기준 금액으로 쿠폰 정책을 검증하고 예상 할인 금액을 계산한다. */
    @Transactional(readOnly = true)
    public CouponPricePreview previewDiscount(long memberId, long memberCouponId, BigDecimal originalAmount) {
        CouponOrderDiscount coupon = couponOrderMapper.findAvailableCouponForOrder(memberCouponId, memberId)
                .orElseThrow(() -> new BusinessException(CouponErrorCode.ORDER_COUPON_UNAVAILABLE));
        if (originalAmount.compareTo(coupon.minimumOrderAmount()) < 0) {
            throw new BusinessException(CouponErrorCode.MINIMUM_ORDER_AMOUNT_NOT_MET);
        }
        BigDecimal discount = coupon.discountType() == DiscountType.PERCENTAGE
                ? originalAmount.multiply(coupon.discountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                : coupon.discountValue().setScale(0, RoundingMode.DOWN);
        if (coupon.maximumDiscountAmount() != null) discount = discount.min(coupon.maximumDiscountAmount());
        discount = discount.min(originalAmount);
        return new CouponPricePreview(discount, originalAmount.subtract(discount));
    }

    public record CouponPricePreview(BigDecimal discountAmount, BigDecimal finalAmount) { }
}
