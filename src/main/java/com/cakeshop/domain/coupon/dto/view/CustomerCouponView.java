package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.coupon.entity.CustomerCouponStatus;

/** 마이페이지 쿠폰함에 필요한 회원 보유 쿠폰 정보다. */
public record CustomerCouponView(
        Long memberCouponId,
        String name,
        String discountDescription,
        String conditionDescription,
        CustomerCouponStatus status,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt
) {
}
