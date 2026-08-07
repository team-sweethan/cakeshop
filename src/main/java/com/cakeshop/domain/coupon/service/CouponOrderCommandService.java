package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.mapper.CouponOrderMapper;
import com.cakeshop.domain.coupon.dto.view.CouponOrderDiscount;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문·결제 담당자가 호출하는 공개 계약이다. 주문 예약·사용 확정·취소 복구의 쿠폰 상태 전이를 소유한다.
 */
@Service
@RequiredArgsConstructor
public class CouponOrderCommandService {

    private final CouponOrderMapper couponOrderMapper;

    /** 주문 생성 트랜잭션에서 쿠폰을 잠그고 할인 금액을 계산한 뒤 결제 대기 상태로 예약한다. */
    @Transactional
    public BigDecimal reserveCouponForOrder(
            long memberId, long memberCouponId, long orderId, BigDecimal originalAmount
    ) {
        CouponOrderDiscount coupon = couponOrderMapper
                .findAvailableCouponForOrderForUpdate(memberCouponId, memberId)
                .orElseThrow(() -> new BusinessException(CouponErrorCode.ORDER_COUPON_UNAVAILABLE));
        if (originalAmount.compareTo(coupon.minimumOrderAmount()) < 0) {
            throw new BusinessException(CouponErrorCode.MINIMUM_ORDER_AMOUNT_NOT_MET);
        }
        BigDecimal discountAmount = calculateDiscount(coupon, originalAmount);
        if (couponOrderMapper.reserveCouponForOrder(memberCouponId, orderId) != 1) {
            throw new BusinessException(CouponErrorCode.ORDER_COUPON_UNAVAILABLE);
        }
        return discountAmount;
    }

    /** 결제 완료된 주문에 예약된 쿠폰을 사용 완료로 확정한다. */
    @Transactional
    public void useReservedCouponForOrder(long orderId) {
        couponOrderMapper.useReservedCouponForOrder(orderId);
    }

    /** 결제 기한이 지난 주문의 예약 쿠폰을 다시 사용할 수 있게 해제한다. */
    @Transactional
    public void releaseReservedCouponForExpiredOrder(long orderId) {
        couponOrderMapper.releaseReservedCouponForExpiredOrder(orderId);
    }

    /**
     * 취소된 주문에 연결된 USED 쿠폰을 복구한다.
     * 이미 복구됐거나 쿠폰을 사용하지 않은 주문은 변경할 행이 없어도 정상적인 멱등 처리로 본다.
     */
    @Transactional
    public void restoreCouponForCanceledOrder(long orderId) {
        couponOrderMapper.restoreCouponForCanceledOrder(orderId);
    }

    private BigDecimal calculateDiscount(CouponOrderDiscount coupon, BigDecimal originalAmount) {
        BigDecimal discount = coupon.discountType() == DiscountType.PERCENTAGE
                ? originalAmount.multiply(coupon.discountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                : coupon.discountValue();
        if (coupon.maximumDiscountAmount() != null) {
            discount = discount.min(coupon.maximumDiscountAmount());
        }
        return discount.min(originalAmount);
    }
}
