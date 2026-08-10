package com.cakeshop.domain.coupon.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponDisplayStatus;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.entity.DiscountType;

/** 관리자 쿠폰 상세 화면에만 필요한 읽기 전용 데이터다. */
public record CouponDetailView(
        Long id,
        String name,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount,
        Integer totalQuantity,
        Integer issuedQuantity,
        LocalDateTime startsAt,
        LocalDateTime expiresAt,
        CouponTargetType targetType,
        CouponDisplayStatus displayStatus
) {

    public static CouponDetailView from(Coupon coupon, CouponDisplayStatus displayStatus) {
        return new CouponDetailView(
                coupon.getId(), coupon.getName(), coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getMinimumOrderAmount(), coupon.getMaximumDiscountAmount(), coupon.getTotalQuantity(),
                coupon.getIssuedQuantity(), coupon.getStartsAt(), coupon.getExpiresAt(), coupon.getTargetType(), displayStatus
        );
    }
}
