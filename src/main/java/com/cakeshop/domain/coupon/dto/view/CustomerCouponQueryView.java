package com.cakeshop.domain.coupon.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cakeshop.domain.coupon.entity.CustomerCouponStatus;
import com.cakeshop.domain.coupon.entity.DiscountType;

/** 회원 쿠폰 목록을 화면용 값으로 조합하기 전, 쿠폰 조회 SQL 결과를 받는 DTO다. */
public record CustomerCouponQueryView(
        Long memberCouponId,
        String name,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount,
        CustomerCouponStatus status,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt
) {
}
