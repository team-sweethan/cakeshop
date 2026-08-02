package com.cakeshop.domain.coupon.entity;

import java.time.LocalDateTime;

/**
 * 관리자 목록과 상세 화면에 표시하는 쿠폰의 현재 상태다.
 * DB의 관리자 상태, 발급 기간, 발급 수량을 조합해 읽기 시점에 계산한다.
 *
 */
public enum CouponDisplayStatus {
    SCHEDULED,  // 발급 예정
    ACTIVE,     // 발급 중
    INACTIVE,   // 발급 중지
    EXHAUSTED,  // 수량 소진
    ENDED;      // 종료

    public static CouponDisplayStatus from(Coupon coupon, LocalDateTime now) {
        if (!coupon.getExpiresAt().isAfter(now)) {
            return ENDED;
        }

        if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            return EXHAUSTED;
        }

        if (coupon.getStatus() == CouponStatus.INACTIVE) {
            return INACTIVE;
        }

        if (coupon.getStartsAt().isAfter(now)) {
            return SCHEDULED;
        }

        return ACTIVE;
    }
}
